# ADR-005: Use Outbox Pattern for Guaranteed Domain Event Delivery

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## Context

When `order` places an order, it must both:
1. Persist the `Order` aggregate to PostgreSQL
2. Publish an `OrderPlaced` event to Kafka so `notification` can react

The naive implementation does these as two separate operations:

```java
orderRepository.save(order);                    // (1) persist
kafkaTemplate.send("order.placed", event);      // (2) publish
```

This creates a **distributed transaction problem**:

| Failure Scenario | Result |
|---|---|
| (1) succeeds, (2) fails (Kafka down) | Order saved but notification never sent — data inconsistency |
| (1) succeeds, (2) succeeds, then crash | No problem |
| (1) fails | Order not saved, event not sent — consistent but lost |
| JVM crash between (1) and (2) | Order saved, event never published — silent inconsistency |

We cannot use 2-Phase Commit (XA transactions) between PostgreSQL and Kafka in a performant system.

---

## Decision

We adopt the **Transactional Outbox Pattern**:

1. Within the same database transaction that saves the aggregate, also insert an `outbox_event` row into a dedicated `outbox` table.
2. A separate **relay process** (Debezium CDC or a polling scheduler) reads unpublished outbox rows and publishes them to Kafka.
3. Once Kafka acknowledges receipt, the relay marks the outbox row as processed (or deletes it).

### Outbox Table Schema

```sql
-- Flyway migration: V2__create_outbox_table.sql
CREATE TABLE outbox_event (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,   -- e.g., 'Order'
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(200) NOT NULL,   -- e.g., 'com.example.events.v1.OrderPlaced'
    payload        JSONB NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'  -- PENDING | PUBLISHED | FAILED
);

CREATE INDEX idx_outbox_status ON outbox_event(status) WHERE status = 'PENDING';
```

### Application Layer Interaction

The secondary port `DomainEventPublisher` hides the outbox mechanism from the application layer:

```java
// application/port/out/EventDispatcher.java
public interface DomainEventPublisher {
    void publish(Object domainEvent);
}
```

```java
// application/command/order/PlaceOrderCommandHandler.java
// @Transactional lives in the infrastructure persistence adapter, not here
public OrderDetailResponse handle(PlaceOrderCommand cmd) {
    Order order = Order.place(cmd.customerId(), cmd.items());
    orderRepository.save(order);
    eventPublisher.publish(new OrderPlaced(...));  // writes to outbox, same TX
    return order;
}
```

### Infrastructure Adapter

```java
// infrastructure/messaging/OutboxDomainEventPublisher.java
@Component
public class OutboxDomainEventPublisher implements DomainEventPublisher {
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(Object domainEvent) {
        OutboxEventJpaEntity entity = OutboxEventJpaEntity.from(domainEvent, objectMapper);
        outboxRepository.save(entity);  // same transaction as aggregate save
    }
}
```

### Relay Strategy: Debezium CDC

We use **Debezium Change Data Capture** reading the PostgreSQL WAL. Debezium runs as a separate container (`debezium/connect:2.6`) in the infrastructure stack.

**Application code has zero Outbox relay logic** — the service only writes to `outbox_event` in the same transaction. Debezium handles the rest.

**How it works:**
1. PostgreSQL runs with `wal_level=logical` (required for CDC)
2. Debezium Connect monitors the `outbox_event` table via the PostgreSQL WAL stream
3. The **Outbox Event Router SMT** (Single Message Transform) transforms each row into a Kafka message:
   - `aggregate_id` → message Key (guarantees ordering per aggregate)
   - `event_type` → routes to the correct Kafka Topic
   - `payload` → message Value

Connector registered in `infrastructure/debezium/connectors/order-outbox-connector.json`:

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

## Consequences

### Positive
- **At-least-once delivery**: The event is guaranteed to reach Kafka as long as PostgreSQL is available. The relay will retry on Kafka failure.
- **Atomicity**: The aggregate state change and the event record are in the same ACID transaction. No partial state possible.
- **No distributed transaction**: No XA, no Saga coordinator needed for this step.

### Negative
- **At-least-once, not exactly-once**: If Debezium reprocesses a WAL segment after a crash, the event may be published more than once. Consumers must be idempotent.
- **Outbox table growth**: Processed rows must be purged periodically (Debezium does not delete them automatically). A scheduled job or Flyway-managed cleanup job deletes rows older than 7 days.
- **Additional infrastructure**: Debezium Connect is a separate container that must be running and healthy.
- **Connector registration**: Connectors must be registered once via the Debezium REST API after Debezium Connect is deployed.

### Consumer Idempotency Requirement

All Kafka consumers must be idempotent. Events carry an `eventId` (UUID). Consumers check a `processed_events` table before processing to avoid double-handling.
