# ADR-009: Kafka 消费者幂等性与数据库支撑的重试机制

- **Status**: Accepted
- **Date**: 2026-03-11
- **Deciders**: Architecture Team

---

## 背景

Kafka 保证**至少一次投递**：由于分区再均衡、Broker 重启，或 Pod 在处理完消息但提交 offset 之前崩溃，消费者可能多次收到同一条消息。因此，任何执行副作用（写入数据库、发送邮件）的消费者都必须具备**幂等性**。

除重复消息外，瞬时故障不可避免。消费者可能因下游依赖暂时不可用而无法处理某条消息。简单地崩溃并依赖 Kafka offset 回放是不可接受的：这会阻塞整个分区直到故障恢复，而 Kafka 内置的重试机制（`RetryTemplate`、`SeekToCurrentErrorHandler`、死信 Topic）将重试策略与 Kafka 基础设施耦合，并且无法追踪单条消息的状态。

必须同时满足以下两个需求：

1. **幂等性**——对同一事件处理两次，产生的可观测结果与处理一次相同。
2. **可靠重试**——瞬时故障必须以指数退避方式重试，且不阻塞 Kafka 分区、不重复处理，并在同一服务的多个运行实例之间安全协作。

---

## 决策

我们在 `seedwork/infrastructure/kafka` 中使用**数据库支撑**的方式实现上述两项保证，独立于 Kafka 自身的错误处理机制。

### 第一部分——消费者幂等性

每个成功处理的事件都以其 `eventId`（UUID）为主键记录到 `processed_events` 表中。在执行任何业务逻辑之前，消费者先查询该表。如果事件已存在，则确认 offset 并立即返回。

```
┌─────────────────────────────────────────────────────────┐
│  @KafkaListener                                          │
│                                                          │
│  processor.process(handler, record, ack)                 │
│      │                                                   │
│      ├─ SELECT FROM processed_events WHERE id = eventId  │
│      │   └─ found → ack, return  (duplicate, skip)       │
│      │   └─ not found →                                  │
│      │       handler.handle(event)                       │
│      │       INSERT INTO processed_events (eventId)      │
│      │       ack                                         │
│      └─ on handler failure →                             │
│          INSERT INTO consumer_retry_events               │
│          ack  (never block the partition)                │
└─────────────────────────────────────────────────────────┘
```

`processed_events` 表是判断"某事件是否已完整处理"的唯一真实来源。该表设计为仅追加、永久保留——行记录永不删除，因此即使某条消息在首次处理很久后被重放，幂等性检查仍然有效。

#### 消费者入口点

提供两种使用模式：

| 模式 | 适用场景 |
|---|---|
| `RetryableKafkaHandler<T>` | 每种事件类型对应一个处理器类。注入 `KafkaMessageProcessor` 并调用 `processor.process(handler, record, ack)`。失败时自动持久化到 `consumer_retry_events`。 |
| `IdempotentKafkaListener`（抽象基类） | 适用于不需要重试的简单消费者。继承该类并调用 `handle(record, ack, eventId, () -> { ... })` 实现基本幂等性，无需重试持久化。 |

`KafkaMessageProcessor` 是两种模式共用的单一事务协调器。它在同一个 `@Transactional` 边界内运行幂等性检查、处理器调用、成功记录和失败持久化，确保在没有持久化处理结果的情况下不会提交 offset。

### 第二部分——基于数据库的声索模式重试

失败的事件连同完整的 Avro 载荷一起存入 `consumer_retry_events` 表，以便独立于 Kafka 进行重放。一个 `@Scheduled` 扫描器（`RetryScheduler`）定期处理到期的重试条目。

#### 为何不使用 Kafka 原生重试？

Kafka 的 `SeekToCurrentErrorHandler` / `DefaultErrorHandler` 通过**回退 offset**重新处理消息，存在以下问题：

- **阻塞分区**：消费者卡在失败消息上，同一分区内的其他消息直到重试耗尽前均无法处理。
- **无法表达单条消息状态**：无法在对消息 A 进行指数退避重试的同时，让同一分区的消息 B 正常继续处理。
- **重试策略与 Kafka 配置耦合**：修改重试间隔需要变更 Broker 或消费者配置，而非应用逻辑。
- **不能跨 Pod 重启存活**：JVM 退出后，内存中的重试状态丢失。

我们的方案将失败记录存入 PostgreSQL，立即确认 offset（解除分区阻塞），并独立于 Kafka 进行重试。

#### 分布式并发问题

在多实例部署中，多个 Pod 同时运行相同的 `@Scheduled` 扫描器。若不加协调，两个实例可能拾取同一条重试条目并向同一处理器分发两次——这正是我们试图避免的重复处理。

一种朴素方案——在整个处理过程中持有 `SELECT FOR UPDATE SKIP LOCKED` 锁——存在两个缺陷：

1. **长时间持锁**：处理一条条目可能耗时数秒。在此期间持有行级锁会阻塞其他实例声索同批次中的其他条目，降低吞吐量。
2. **事务污染**：当所有条目在单个 `@Transactional` 方法内处理，且某个处理器抛出 `RuntimeException` 时，Spring 会将整个事务标记为 `rollback-only`。即使异常被捕获，后续的 `incrementAttempt()` 调用仍处于一个注定回滚的事务中，最终的 `commit()` 会抛出 `UnexpectedRollbackException`，**整个批次的所有状态更新全部丢失**。

#### 解决方案：声索 + REQUIRES_NEW

处理过程拆分为两个 Spring 管理方法，分属两个独立类（这是激活 Spring 基于代理的 `@Transactional` 所必需的）：

```
RetryScheduler.scan()  — no @Transactional, orchestration only
    │
    ├── RetryEntryProcessor.claimBatch()  ── @Transactional (short)
    │       SELECT FOR UPDATE SKIP LOCKED  → rows [A, B, C]
    │       nextRetryAt = now + claimDurationMs   (claim marker)
    │       commit  ← locks released; rows invisible to other instances
    │                 because nextRetryAt is now in the future
    │       return [id_A, id_B, id_C]
    │
    ├── RetryEntryProcessor.processEntry(id_A)  ── @Transactional(REQUIRES_NEW)
    │       re-fetch row A
    │       handler.handle(event)
    │       on success → delete row, markProcessed(eventId)  commit
    │       on failure → incrementAttempt / markDeadLettered  commit
    │                    (nextRetryAt overwritten with real backoff)
    │
    ├── RetryEntryProcessor.processEntry(id_B)  ── @Transactional(REQUIRES_NEW)
    │       ...
    │
    └── RetryEntryProcessor.processEntry(id_C)  ── @Transactional(REQUIRES_NEW)
            ...
```

**声索机制为何能跨实例有效防止重复处理：**

`claimBatch()` 提交后，行记录不再被锁定——但其 `nextRetryAt` 已被推进到 `now + claimDurationMs`（默认 5 分钟）。仓库查询条件为 `WHERE nextRetryAt <= now`，因此其他实例的 `claimBatch()` 调用在声索到期前不会看到这些行。无需分布式锁管理器或外部协调。

**REQUIRES_NEW 为何能修复事务污染：**

每个 `processEntry()` 在完全独立的事务中运行。若某个处理器抛出异常导致其事务被标记为 `rollback-only`，只有该条目的事务回滚。随后的失败处理代码（在同一 `REQUIRES_NEW` 事务内）能够正确持久化 `incrementAttempt()` 或 `markDeadLettered()` 后提交。同批次中的其他条目不受影响。

**崩溃安全性：**

若 Pod 在 `claimBatch()` 提交后、所有 `processEntry()` 调用完成前崩溃，被声索的行不会丢失。其 `nextRetryAt` 声索到期后，任何存活实例的下一次扫描周期都会重新声索并处理这些行。`processEntry()` 内部的幂等性检查（`processedEventStore.isProcessed()`）可防止某条目在崩溃前已被成功处理的情况下被再次处理。

#### 重试生命周期

```
First failure (in @KafkaListener):
  KafkaMessageProcessor.process()
      └── INSERT consumer_retry_events (attemptCount=1, nextRetryAt=now)

Retry scan (RetryEntryProcessor.processEntry):
  success  → DELETE consumer_retry_events
              INSERT processed_events
  failure  → attemptCount < maxAttempts:
                UPDATE nextRetryAt = now + backoff(attemptCount)  ← exponential
             attemptCount >= maxAttempts:
                UPDATE deadLettered=true, deadLetteredAt=now  ← dead-letter
```

退避公式：`min(baseMs × 2^(attempt-1), maxMs)`——设有上限，防止延迟无限增长。

#### 配置

```yaml
consumer:
  retry:
    enabled: true          # disable to turn off the retry scheduler entirely
    interval-ms: 10000     # how often the scanner runs (fixed delay)
    max-attempts: 5        # attempts before dead-lettering
    batch-size: 100        # entries claimed per scan cycle
    claim-duration-ms: 300000  # claim window (5 min); must exceed worst-case processing time
    backoff:
      base-ms: 1000        # first retry after 1s
      max-ms: 3600000      # capped at 1h
```

### 第三部分——类职责

```
kafka/
├── RetryableKafkaHandler<T>          interface  consumer implements per event type
├── KafkaMessageProcessor             @Transactional coordinator for @KafkaListener methods
├── IdempotentKafkaListener           abstract base for simple consumers without retry
├── ProcessedEventStore               public facade over processed_events table
├── RetryHandlerRegistry              maps Avro class name → RetryableKafkaHandler for retry dispatch
├── ProcessedEventJpaEntity           (package-private) JPA entity for processed_events
├── ProcessedEventJpaRepository       (package-private) Spring Data repo
└── retry/
    ├── RetryScheduler                @Scheduled orchestrator; no @Transactional
    ├── RetryEntryProcessor           claim + per-entry REQUIRES_NEW processing
    ├── ConsumerRetryPersistenceAdapter  public adapter: saveNewFailure / incrementAttempt / markDeadLettered
    ├── RetryProperties               @ConfigurationProperties record
    ├── ConsumerRetryEventJpaEntity   (package-private) JPA entity for consumer_retry_events
    ├── ConsumerRetryEventJpaRepository (package-private) Spring Data repo with SKIP LOCKED query
    ├── KafkaIdempotencyAutoConfiguration  auto-wires ProcessedEventStore + KafkaMessageProcessor
    └── ConsumerRetryAutoConfiguration    auto-wires retry beans; conditional on consumer.retry.enabled
```

---

## 影响

### 积极影响

- **分区永不阻塞**：失败消息立即被确认，在带外重试。
- **单条消息独立重试状态**：每条消息拥有独立的重试次数、退避计划和死信标志，互不影响。
- **跨 Pod 重启存活**：重试状态存储于 PostgreSQL，而非 JVM 内存。
- **无需外部协调的分布式安全**：声索模式仅利用现有 PostgreSQL 行防止跨实例重复处理，无需 Redis、Zookeeper 或分布式锁管理器。
- **单条目事务隔离**：`REQUIRES_NEW` 确保某个处理器失败不会污染同批次其他条目的状态更新。
- **死信可观测**：`consumer_retry_events` 中 `dead_lettered=true` 的行永久可见，便于监控和人工干预。
- **零 Kafka 耦合**：重试策略以应用配置和 Java 代码表达，无需更改 Kafka Broker 配置。

### 消极影响

- **每服务额外表**：使用重试机制的每个服务都需要 `processed_events` 和 `consumer_retry_events` 表及相应的 Flyway 迁移。
- **轮询开销**：即使没有待重试条目，调度器仍会每隔 `interval-ms` 运行一次。通过批次为空时提前返回加以缓解。
- **声索时长需调优**：`claimDurationMs` 必须大于单条目处理的最坏情况耗时。若设置过小，某条处理较慢的条目可能被两个实例同时声索。幂等性检查提供了安全网，但声索时长应保守设置。
- **至少一次重试投递**：`handler.handle()`、`deleteById` 和 `markProcessed` 均参与同一个 `REQUIRES_NEW` 事务。若 Pod 在该事务提交前崩溃，三个操作全部回滚——重试行保留，声索到期后下一次扫描周期会重新调用处理器。因此处理器可能执行多次，必须保证幂等性。`processEntry` 开头的 `findById(...).orElse(null)` 防卫语句处理另一实例已完成该条目（行已删除）的竞态情况；`isProcessed()` 是针对未来设计变更中理论上存在的部分状态场景的额外安全网。
- **死信行需要运维流程**：`dead_lettered=true` 的行不会自动处理。运维人员必须排查根因，然后将 `dead_lettered` 重置为 `false` 并将 `nextRetryAt` 设为当前时间以重新尝试，或接受丢失并删除该行。
