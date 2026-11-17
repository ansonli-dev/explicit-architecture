---
type: integration
title: Outbox, deduplication, and retry delivery
description: Traces event persistence, scheduler and Debezium relays, consumer acknowledgments, and database-backed retries, including current delivery limitations.
tags: [outbox, kafka, retry, transactions, reliability]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-bcb277da804158bdbcb9b977
    resource: repo://order/helm/templates/debezium-connector-job.yaml
  - id: openwiki-source-85f28f64471f782c274d0aa6
    resource: repo://order/src/main/resources/application.yml
  - id: openwiki-source-7cee329bba191761e82521cb
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/KafkaMessageProcessor.java
  - id: openwiki-source-b13d23701cba59f75c31c4b7
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/retry/ConsumerRetryEventJpaRepository.java
  - id: openwiki-source-16cdb6410cfd7377f1f9e999
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/retry/RetryEntryProcessor.java
  - id: openwiki-source-0ef2c7e66a8ae56f09ba7822
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/retry/RetryScheduler.java
  - id: openwiki-source-4596a1988c05ae128fc637cf
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/outbox/KafkaOutboxEventPublisher.java
  - id: openwiki-source-8433dba3fb1f8b2b18890a93
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/outbox/OutboxJpaRepository.java
  - id: openwiki-source-454dba5fabfd335c9d3fba83
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/outbox/OutboxRelayScheduler.java
  - id: openwiki-source-34cc31c0cb0be28c947f7c12
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/outbox/OutboxWriteListener.java
  - id: openwiki-source-3e3aafba1e5d98237888a5df
    resource: repo://seedwork/src/main/resources/db/seedwork/V0001__seedwork_outbox_events.sql
  - id: openwiki-source-70d707152a2e5d097355ad4f
    resource: repo://seedwork/src/main/resources/db/seedwork/V0002__seedwork_processed_events.sql
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Outbox, deduplication, and retry delivery

Seedwork owns the technical persistence and scheduling machinery; each service owns the mapping and business handler. [Event contracts](event-contracts.md) describes Avro payloads; [Seedwork](../concepts/seedwork.md) describes how domain events reach Spring listeners.

## Producer transaction and relay

`OutboxWriteListener` runs `BEFORE_COMMIT`. For a mapped domain event it writes `outbox_event` in the aggregate-save transaction, storing event ID, aggregate ID, topic, aggregate-based message key, class name, and serialized bytes. The migration makes event ID unique. Serialization occurs through the service mapper before that transaction commits, so serialization failures can fail the write rather than waiting for relay.

Two relay strategies share these rows:

| Strategy | Configuration and behavior |
|---|---|
| Scheduler | Enabled when `outbox.relay.strategy=scheduler` or absent. Every 5 seconds by default, loads unpublished rows ordered by creation time, sends stored bytes and waits for completion, then marks each successful row published. Per-row send failures are logged. Catalog explicitly selects this strategy. |
| Debezium | Order selects `debezium`, suppressing its scheduler bean. A Helm hook registers `order-outbox-connector`, tails `public.outbox_event` with `order_outbox_slot`, routes by the row's topic, and forwards `avro_payload` through `ByteArrayConverter`. |

The scheduler's query has no batch limit or row-claim lock. Its delivery is at least once: a send can succeed before the database mark commits, and multiple relay instances can select the same pending rows. A SQL comment mentioning `SKIP LOCKED` does not describe the actual outbox query.

The configured Debezium outbox connector uses `snapshot.mode=never` and disables heartbeats. Its configuration does not backfill pre-existing rows through an initial snapshot or mark their `published` flags. Consequently, the shared unpublished-row gauge is not a reliable Debezium delivery-lag measurement by itself. The [order read-model connector](order-read-model.md) has a different snapshot policy.

## Consumer acknowledgments and deduplication

Catalog and Notification delegate Kafka records to the standalone, transactional `KafkaMessageProcessor` bean:

1. Extract the event ID through the typed handler.
2. If `processed_events` already contains it, acknowledge and return.
3. Otherwise invoke the business handler and mark the event processed in the surrounding transaction.
4. On success, register acknowledgment for `afterCommit`.
5. On an exception from handling or marking, attempt to store a retry record and acknowledge in `finally`.

Deduplication uses an event-ID primary key in each service database, not a key combining topic and consumer group. The separate `processSimple` path has the success/deduplication behavior but lets handler failures escape without retry persistence.

The failure path has a narrower durability guarantee than the success path: retry persistence errors are logged and swallowed, and acknowledgment is still attempted before transaction completion. A failed retry insert or transaction rollback can therefore leave an acknowledged event without a durable retry. Initial retry rows also record the consumer group as the literal `unknown`.

Handler semantics affect this flow. Catalog catches individual stock-release failures; Notification catches email-send failures and saves a failed status. Those handled failures do not automatically reach the generic retry branch. See [Catalog inventory](../workflows/catalog-inventory.md) and [Notifications](../workflows/notifications.md).

## Retry lifecycle

Retry rows contain raw Avro binary and the Java Avro class name, unlike the producer outbox's Confluent-framed bytes. Retry dispatch loads the named class, decodes the record, and routes through `RetryHandlerRegistry`, populated from Spring `RetryableKafkaHandler` beans. Removing or renaming an event class or handler can make persisted retries undecodable or undispatchable.

The scheduler invokes a separate processor bean. `claimBatch()` uses a pessimistic write query with skip-locked hint, selecting due non-dead-lettered rows below the attempt limit. It advances `nextRetryAt` to a claim-expiry time in a short transaction. Each claimed ID is then processed in a `REQUIRES_NEW` transaction. Already-processed events are cleaned up; successful retries delete the row and mark the event processed. Failed retries increment the count and calculate capped exponential backoff.

The no-argument properties constructor declares a 10-second interval, 100-row batch, five-attempt limit, five-minute claim, and backoff base/cap of one second/one hour. Check bound runtime properties when overriding them. Claim expiry allows a crashed worker's entries to become due again; it is not a fence against a worker running beyond its claim duration.

Two source-level limits matter when investigating stuck work:

- The due query requires `attemptCount < maxAttempts`, while dead-lettering requires the incremented count to be `> maxAttempts`. Ordinary repeated failures reach the limit and stop being selected before that branch executes.
- `DeadLetteredEvent` is published inside `processEntry`, not by an after-commit callback. Listener phase would determine when a transaction-aware subscriber reacts. Also, an exception escaping a per-entry transaction ends the scheduler loop because `scan()` does not catch around each call.

## Investigation route

Trace [KafkaMessageProcessor](../../seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/KafkaMessageProcessor.java), [RetryEntryProcessor](../../seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/retry/RetryEntryProcessor.java), its [selection query](../../seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/retry/ConsumerRetryEventJpaRepository.java), and the [outbox connector](../../order/helm/templates/debezium-connector-job.yaml). Inspect business state alongside `outbox_event`, `processed_events`, and `consumer_retry_events`; their flags represent different stages. Existing consumer-routing tests do not establish all crash, concurrency, and acknowledgment guarantees. See [Verification](../testing/verification-map.md) and [Runtime](../operations/runtime-and-deployment.md).
