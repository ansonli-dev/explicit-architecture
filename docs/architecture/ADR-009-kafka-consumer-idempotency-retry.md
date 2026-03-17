# ADR-009: Kafka Consumer Idempotency and DB-Backed Retry

- **Status**: Accepted
- **Date**: 2026-03-11
- **Deciders**: Architecture Team

---

## Context

Kafka guarantees **at-least-once delivery**: a consumer may receive the same message more than once due to rebalances, broker restarts, or a pod crashing after processing but before committing the offset. Any consumer that performs side effects (writes to a database, sends an email) must therefore be **idempotent**.

Beyond duplicates, transient failures are inevitable. A consumer may fail to process a message because a downstream dependency is temporarily unavailable. Simply crashing and relying on Kafka offset replay is unacceptable: it blocks the entire partition until the failure is resolved, and Kafka's built-in retry (`RetryTemplate`, `SeekToCurrentErrorHandler`, dead-letter topics) couples retry policy to the Kafka infrastructure and prevents per-message state tracking.

Two requirements must be met:

1. **Idempotency** — processing the same event twice must produce the same observable outcome as processing it once.
2. **Reliable retry** — transient failures must be retried with exponential backoff without blocking the Kafka partition, without double-processing, and safely across multiple running instances of the same service.

---

## Decision

We implement both guarantees inside `seedwork/infrastructure/kafka` using a **database-backed approach** that is independent of Kafka's own error-handling machinery.

### Part 1 — Consumer Idempotency

Every successfully processed event is recorded in a `processed_events` table using its `eventId` (UUID) as the primary key. Before any business logic runs, the consumer checks this table. If the event is already present, it acknowledges the offset and returns immediately.

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

The `processed_events` table is the single source of truth for "has this event been fully processed". It is intentionally append-only and permanent — rows are never deleted, which allows the idempotency check to work even if a message is replayed long after its first processing.

#### Entry points for consumers

Two usage patterns are provided:

| Pattern | When to use |
|---|---|
| `RetryableKafkaHandler<T>` | One handler class per event type. Inject `KafkaMessageProcessor` and call `processor.process(handler, record, ack)`. Failures are automatically persisted to `consumer_retry_events`. |
| `IdempotentKafkaListener` (abstract base class) | For simple consumers that do not need retry. Extend this class and call `handle(record, ack, eventId, () -> { ... })` for basic idempotency without retry persistence. |

`KafkaMessageProcessor` is the single transactional coordinator for both patterns. It runs the idempotency check, handler invocation, success recording, and failure persistence inside one `@Transactional` boundary, ensuring the offset is never committed without a durable record of the outcome.

### Part 2 — DB-Backed Retry with Claim Pattern

Failed events are stored in `consumer_retry_events` with their full Avro payload so they can be replayed independently of Kafka. A `@Scheduled` scanner (`RetryScheduler`) periodically processes due entries.

#### Why not Kafka-native retry?

Kafka's `SeekToCurrentErrorHandler` / `DefaultErrorHandler` retries by **seeking the offset backwards** and reprocessing. This:

- **Blocks the partition**: the consumer stalls on the failing message; no other messages on the same partition are processed until the retry is exhausted.
- **Cannot express per-message state**: there is no way to retry message A with exponential backoff while message B (same partition) proceeds normally.
- **Couples retry policy to Kafka config**: changing retry intervals requires broker or consumer config changes, not application logic.
- **Does not survive pod restarts**: in-memory retry state is lost when the JVM exits.

Our approach stores the failure in PostgreSQL, acknowledges the offset immediately (unblocking the partition), and retries independently of Kafka.

#### Distributed concurrency problem

In a multi-instance deployment, multiple pods run the same `@Scheduled` scanner simultaneously. Without coordination, two instances can pick up the same retry entry and dispatch it to the same handler twice — exactly the double-processing we are trying to prevent.

A naive solution — `SELECT FOR UPDATE SKIP LOCKED` held for the duration of processing — has two flaws:

1. **Long-held locks**: processing one entry may take seconds. Holding a row-level lock for that duration blocks other instances from claiming other entries in the same batch, reducing throughput.
2. **Transaction poisoning**: when all entries are processed inside a single `@Transactional` method and a handler throws `RuntimeException`, Spring marks the entire transaction as `rollback-only`. Even though the exception is caught, subsequent `incrementAttempt()` calls are in a doomed transaction. The final `commit()` raises `UnexpectedRollbackException` and **all state updates for the entire batch are lost**.

#### Solution: Claim + REQUIRES_NEW

Processing is split across two Spring-managed methods in two separate classes (required to activate Spring's proxy-based `@Transactional`):

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

**Why claim works across instances:**

After `claimBatch()` commits, the rows are no longer locked — but their `nextRetryAt` has been advanced to `now + claimDurationMs` (default: 5 minutes). The repository query filters `WHERE nextRetryAt <= now`, so other instances' `claimBatch()` calls will not see these rows until the claim expires. No distributed lock manager or external coordination is needed.

**Why REQUIRES_NEW fixes transaction poisoning:**

Each `processEntry()` runs in a completely independent transaction. If a handler throws and its transaction is marked `rollback-only`, only that one entry's transaction rolls back. The `handleFailure()` code that follows (inside the same `REQUIRES_NEW` transaction) correctly persists `incrementAttempt()` or `markDeadLettered()` before committing. Sibling entries in the same scan batch are unaffected.

**Crash safety:**

If a pod crashes after `claimBatch()` commits but before all `processEntry()` calls complete, the claimed rows are not lost. Their `nextRetryAt` claim expires after `claimDurationMs`, and the next scan cycle on any live instance will re-claim and reprocess them. The idempotency check (`processedEventStore.isProcessed()`) inside `processEntry()` prevents double-processing if a later instance picks up an entry that was already successfully handled before the crash.

#### Retry lifecycle

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

Backoff formula: `min(baseMs × 2^(attempt-1), maxMs)` — capped to prevent unbounded delays.

#### Configuration

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

### Part 3 — Class Responsibilities

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

## Consequences

### Positive

- **Partition never blocked**: failed messages are acknowledged immediately and retried out-of-band.
- **Per-message retry state**: each message has its own attempt count, backoff schedule, and dead-letter flag — independent of all other messages.
- **Survives pod restarts**: retry state is in PostgreSQL, not JVM memory.
- **Distributed-safe without external coordination**: the claim pattern prevents double-processing across instances using only the existing PostgreSQL row, with no Redis, Zookeeper, or distributed lock manager.
- **Transaction isolation per entry**: `REQUIRES_NEW` ensures one handler failure does not corrupt the state updates of other entries in the same batch.
- **Observable dead letters**: `consumer_retry_events` rows with `dead_lettered=true` are permanently visible for monitoring and manual intervention.
- **Zero Kafka coupling**: retry policy is expressed in application config and Java code; no Kafka broker config changes needed.

### Negative

- **Additional tables per service**: each service that uses the retry mechanism needs `processed_events` and `consumer_retry_events` tables and the corresponding Flyway migrations.
- **Polling overhead**: the scheduler runs every `interval-ms` even when there is nothing to retry. Mitigated by the early-return on empty batch.
- **Claim duration must be tuned**: `claimDurationMs` must be larger than the worst-case single-entry processing time. If it is set too small, a slow entry may be claimed by two instances simultaneously. The idempotency check provides a safety net, but the claim duration should be set conservatively.
- **At-least-once retry delivery**: `handler.handle()`, `deleteById`, and `markProcessed` all participate in the same `REQUIRES_NEW` transaction. If a pod crashes before that transaction commits, all three operations roll back together — the retry row survives, the claim expires, and the next scan cycle re-invokes the handler. The handler may therefore execute more than once; it must be idempotent. The `findById(...).orElse(null)` guard at the start of `processEntry` handles the race where another instance already completed the entry (row deleted); `isProcessed()` is an additional safety net for theoretical partial-state scenarios in future design changes.
- **Dead-letter rows require operational process**: rows with `dead_lettered=true` are not automatically resolved. An operator must inspect them, fix the root cause, and either reset `dead_lettered=false` / `nextRetryAt=now` for a fresh retry, or accept the loss and delete them.
