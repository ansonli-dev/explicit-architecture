# ADR-005: 使用 Outbox 模式保障领域事件的可靠投递

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## 背景

当 `order` 服务下单时，必须同时完成以下两件事：
1. 将 `Order` 聚合持久化到 PostgreSQL
2. 向 Kafka 发布 `OrderPlaced` 事件，以便 `notification` 服务作出响应

朴素实现将这两步作为独立操作：

```java
orderRepository.save(order);                    // (1) 持久化
kafkaTemplate.send("order.placed", event);      // (2) 发布
```

这引发了**分布式事务问题**：

| 故障场景 | 结果 |
|---|---|
| (1) 成功，(2) 失败（Kafka 宕机） | 订单已保存但通知永远不会发送——数据不一致 |
| (1) 成功，(2) 成功，随后崩溃 | 无问题 |
| (1) 失败 | 订单未保存，事件未发送——一致但数据丢失 |
| JVM 在 (1) 和 (2) 之间崩溃 | 订单已保存，事件永远不会发布——静默不一致 |

在高性能系统中，我们无法在 PostgreSQL 和 Kafka 之间使用两阶段提交（XA 事务）。

---

## 决策

我们采用**事务性 Outbox 模式**：

1. 在保存聚合的同一数据库事务中，同时向专用的 `outbox` 表插入一条 `outbox_event` 记录。
2. 一个独立的**中继进程**（Debezium CDC 或轮询调度器）读取未发布的 outbox 记录并将其发布到 Kafka。
3. 一旦 Kafka 确认接收，中继将 outbox 记录标记为已处理（或将其删除）。

### Outbox 表结构

```sql
-- Flyway migration: V2__create_outbox_table.sql
CREATE TABLE outbox_event (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,   -- 例如 'Order'
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(200) NOT NULL,   -- 例如 'com.example.events.v1.OrderPlaced'
    payload        JSONB NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'  -- PENDING | PUBLISHED | FAILED
);

CREATE INDEX idx_outbox_status ON outbox_event(status) WHERE status = 'PENDING';
```

### 应用层交互

次端口 `DomainEventPublisher` 对应用层隐藏了 outbox 机制：

```java
// application/port/out/EventDispatcher.java
public interface DomainEventPublisher {
    void publish(Object domainEvent);
}
```

```java
// application/command/order/PlaceOrderCommandHandler.java
// @Transactional 位于基础设施持久化适配器中，不在此处
public OrderDetailResponse handle(PlaceOrderCommand cmd) {
    Order order = Order.place(cmd.customerId(), cmd.items());
    orderRepository.save(order);
    eventPublisher.publish(new OrderPlaced(...));  // 写入 outbox，同一事务
    return order;
}
```

### 基础设施适配器

```java
// infrastructure/messaging/OutboxDomainEventPublisher.java
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(Object domainEvent) {
        OutboxEventJpaEntity entity = OutboxEventJpaEntity.from(domainEvent, objectMapper);
        outboxRepository.save(entity);  // 与聚合保存在同一事务中
    }
}
```

### 中继策略：Debezium CDC

我们使用 **Debezium 变更数据捕获**读取 PostgreSQL WAL。Debezium 作为独立容器（`debezium/connect:2.6`）运行于基础设施栈中。

**应用代码中没有任何 Outbox 中继逻辑**——服务只负责在同一事务中写入 `outbox_event`，其余工作由 Debezium 完成。

**工作原理：**
1. PostgreSQL 以 `wal_level=logical` 运行（CDC 所必需）
2. Debezium Connect 通过 PostgreSQL WAL 流监控 `outbox_event` 表
3. **Outbox Event Router SMT**（单消息转换）将每一行转换为 Kafka 消息：
   - `aggregate_id` → 消息 Key（保证每个聚合的消息有序）
   - `event_type` → 路由到正确的 Kafka Topic
   - `payload` → 消息 Value

连接器注册于 `infrastructure/debezium/connectors/order-outbox-connector.json`：

```json
{
  "name": "order-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.dbname": "order",
    "table.include.list": "public.outbox_event",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.topic.replacement": "bookstore.${routedByValue}"
  }
}
```

---

## 影响

### 积极影响
- **至少一次投递**：只要 PostgreSQL 可用，事件就能保证到达 Kafka。中继在 Kafka 故障时会重试。
- **原子性**：聚合状态变更和事件记录处于同一 ACID 事务中，不存在部分状态的可能。
- **无分布式事务**：此步骤无需 XA，也无需 Saga 协调器。

### 消极影响
- **至少一次，而非恰好一次**：如果 Debezium 在崩溃后重新处理 WAL 段，事件可能被发布多次。消费者必须实现幂等性。
- **outbox 表膨胀**：已处理的记录必须定期清理（Debezium 不会自动删除它们）。定时任务或 Flyway 管理的清理任务负责删除 7 天以上的记录。
- **额外基础设施**：Debezium Connect 是一个必须持续运行且保持健康的独立容器。
- **连接器注册**：连接器必须在 Debezium Connect 部署后通过 Debezium REST API 注册一次。

### 消费者幂等性要求

所有 Kafka 消费者必须实现幂等性。事件携带 `eventId`（UUID）。消费者在处理前检查 `processed_events` 表，以避免重复处理。
