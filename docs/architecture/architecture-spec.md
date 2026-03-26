# Architecture Specification

**Online Bookstore — Explicit Architecture + Clarified Architecture**

> Version 1.0 · March 2026
>
> **Authority chain:** [`clarified-architecture-en.md`](clarified-architecture/clarified-architecture-en.md) is the canonical source for architectural principles. This document translates those principles into project-specific naming, package structure, and implementation rules. In any conflict between this document and `clarified-architecture-en.md`, the latter wins. In any conflict between this document and `CLAUDE.md`, this document wins.
>
> Upstream references:
> - [Herberto Graça — Explicit Architecture](https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/)
> - [`clarified-architecture-en.md`](clarified-architecture/clarified-architecture-en.md) — canonical architectural principles (authoritative)
> - Project code — source of truth for what is actually implemented

---

## Table of Contents

- [Part I: Philosophy & Foundations](#part-i-philosophy--foundations)
- [Part II: The Five Clarifications](#part-ii-the-five-clarifications)
- [Part III: Package Structure](#part-iii-package-structure)
- [Part IV: CQRS Implementation](#part-iv-cqrs-implementation)
- [Part V: Event-Driven Architecture](#part-v-event-driven-architecture)
- [Part VI: Transaction Rules](#part-vi-transaction-rules)
- [Part VII: Naming Conventions](#part-vii-naming-conventions)
- [Part VIII: Testing Strategy](#part-viii-testing-strategy)
- [Part IX: Anti-Patterns](#part-ix-anti-patterns)
- [Part X: Technology Decisions (ADR Index)](#part-x-technology-decisions-adr-index)

---

## Part I: Philosophy & Foundations

### 1.1 Why Explicit Architecture

Traditional Spring Boot layered architecture (`controller → service → repository`) has three systemic problems:

1. **Framework leakage** — `@Transactional`, JPA annotations, and Spring stereotypes end up in business logic, making it untestable without a Spring context.
2. **No enforced boundaries** — Nothing prevents a controller from calling a repository directly.
3. **Hidden intent** — A package named `service/` says nothing about what the application does.

Explicit Architecture fuses DDD, Hexagonal (Ports & Adapters), Onion, and Clean Architecture into one model. Every layer has a name that reflects its *role*, not its technical tier. Business logic is testable without any infrastructure.

### 1.2 The Iron Rule

> **The Domain Model depends on nothing outside itself.**
>
> No infrastructure interfaces, no application-layer types, no framework annotations with runtime behavior. This is the single invariant that cannot be relaxed.

What "runtime behavior" means in practice:

| Annotation | Verdict | Reason |
|------------|---------|--------|
| `@Entity` | **Forbidden** in domain | Alters how the object is loaded and flushed by JPA |
| `@Transactional` | **Forbidden** in domain/services | Generates a Spring proxy; creates hidden infra dependency |
| `@OneToMany`, `@ManyToOne` | **Forbidden** in domain | JPA relationship management — infrastructure concern |
| `@Lazy` | **Forbidden** in domain | Load strategy — infrastructure concern |
| `@Component`, `@Service` | **Acceptable** on Domain Services | Discovery-only; no runtime behavior; `new DomainService()` works identically |

### 1.3 The Four Zones

| Zone | Contains | Depends On | Key Constraint |
|------|----------|------------|----------------|
| **Domain Model** | Entities, Value Objects, Domain Events, Specifications | Nothing | Zero imports from application/infra/framework |
| **Domain Services** | Stateless cross-aggregate pure logic | Domain Model only | No I/O; no Repository injection; no `@Transactional` |
| **Application** | Handlers, Commands, Queries, Ports, DTOs | Domain Model + Domain Services | Orchestration only; no JPA/Redis/Kafka imports |
| **Infrastructure + Interfaces** | Adapters, Controllers, ORM, Messaging | All inner zones via Ports | Implements Ports; never imported by inner zones |

### 1.4 Dependency Direction

```
Interfaces ──┐
             ├──► Application ──► Domain Services ──► Domain Model
Infrastructure┘
```

At runtime the flow reverses: Controller calls Handler, Handler calls Domain, Domain has no knowledge of its caller.

### 1.5 Ports & Adapters

- **Primary (Driving) Adapters** — REST Controllers, Kafka Consumers. They live in `interfaces/`, receive external input, and dispatch Commands/Queries via the Bus.
- **Secondary (Driven) Adapters** — JPA repositories, Redis clients, Kafka producers, HTTP clients. They live in `infrastructure/`, implement Ports defined by the Application layer (or Domain layer for write-side repositories).
- **The Bus as Primary Port** — `CommandBus` and `QueryBus` are the single entry points for the driving side. Controllers depend only on these two interfaces; they never inject concrete Handler classes.

---

## Part II: The Five Clarifications

Herberto Graça's article leaves five decision points open. This project makes explicit, project-wide choices for each.

### 2.1 Clarification 1 — One Home for Use Cases

**Problem:** Explicit Architecture allows logic in Application Services *or* Command Handlers, creating ambiguity.

**Decision:** This project uses **Command/Query Handlers via the Bus** as the sole use-case container. Application Services as a class do not exist. `*Service` named classes only exist as Domain Services.

```java
// Handler anatomy — the only place use-case logic lives
@Service
public class PlaceOrderCommandHandler implements CommandHandler<PlaceOrderCommand, PlaceOrderResult> {
    @Override
    public PlaceOrderResult handle(PlaceOrderCommand cmd) {
        // 1. Load via ports (no direct JPA/Kafka here)
        // 2. Call Domain Service / Aggregate method
        // 3. Persist via ports
        // 4. No event dispatching — publishing is a side-effect of persistence
    }
}
```

### 2.2 Clarification 2 — Domain Service Purity

**Problem:** When a Domain Service needs data it cannot fetch itself (because it must not hold Repository references), layers blur.

**Decision:** Domain Services are **pure functions**. They receive all inputs as parameters. The Handler performs all I/O.

```java
// Domain Service — pure; injected with no ports
public class OrderPricingService {
    public PricingResult calculate(List<OrderItem> items, DiscountPolicy policy) {
        // pure computation only
    }
}

// Handler — all I/O here, then delegate
public PlaceOrderResult handle(PlaceOrderCommand cmd) {
    var items = /* load items */;
    var policy = /* load policy */;
    var pricing = pricingService.calculate(items, policy); // pure call
    // persist...
}
```

### 2.3 Clarification 3 — Repository Interface Placement

**Problem:** Where do Repository interfaces live — in the Domain layer or Application layer?

**Decision: Split write-side and read-side cleanly.**

| Path | Port location | Rationale |
|------|--------------|-----------|
| **Write-side** (Commands) | `domain/ports/` | `OrderPersistence` is a domain concept — "the collection of all Orders." Parameters that represent domain objects use domain types (`OrderId`, `Order`), not raw `UUID`/`String`; primitive types for pagination or simple filters are acceptable. |
| **Read-side** (Queries) | `application/port/outbound/` | `OrderSearchRepository`, `OrderReadRepository` are infrastructure abstractions with no domain meaning. |

```
domain/ports/
└── OrderPersistence.java       ← save(Order), findById(OrderId)

application/port/outbound/
├── OrderSearchRepository.java  ← findById(UUID) → OrderDetailResponse (bypasses domain)
├── OrderReadRepository.java    ← findDetailById(OrderId) → OrderDetailResponse (JPA projection)
└── CatalogClient.java          ← checkStock/reserve/release (HTTP cross-service call)
```

The key distinction: `OrderPersistence` only speaks domain types (`Order`, `OrderId`). `OrderSearchRepository` returns DTOs directly — it has no domain meaning.

### 2.4 Clarification 4 — Event Registry over Shared Kernel

**Problem:** A Shared Kernel of shared Java classes grows unbounded and couples all services.

**Decision:** The `shared-events` module is an **Event Registry** — an Avro SDK containing only schema-generated classes, no business logic.

```
shared-events/src/main/avro/
└── com/example/events/v1/
    ├── OrderPlaced.avsc
    ├── OrderCancelled.avsc
    ├── StockReserved.avsc
    └── ...

⬇ build-time Avro code generation

shared-events/build/generated/avro/
└── com/example/events/v1/
    ├── OrderPlaced.java        ← SpecificRecord, no business logic
    └── ...
```

Rules:
- Domain objects (`domain/event/`) never import Avro classes.
- Only `infrastructure/messaging/outbox/{Service}OutboxMapper.java` maps domain events → Avro.
- Schema changes follow [ADR-008](adr/ADR-008-shared-events-versioning.md): BACKWARD-compatible changes → PATCH/MINOR bump; breaking changes → new version namespace (`v2/`) + MAJOR bump.

### 2.5 Clarification 5 — Cross-Service Data Consistency

**Problem:** Microservices need each other's data. Shared databases create coupling.

**Decision:** Event-driven local copies via the **Outbox Pattern** (not shared database views — we are in microservices topology).

Each service owns its PostgreSQL database exclusively. Cross-service data flows through Kafka:

```
Order places → OrderPlaced event in outbox → Kafka → Notification consumes
Catalog releases stock → OrderCancelled event consumed by Catalog → stock released
```

Cross-service state changes (e.g., stock release on order cancel) are **always event-driven**. No synchronous HTTP calls for cross-service state mutation.

---

## Part III: Package Structure

### 3.1 Canonical Layout (all services)

```
com.example.{service}/
├── domain/                          ← Zero framework dependencies. Pure Java.
│   ├── model/                       ← Aggregates, Entities, Value Objects (records), Enums
│   ├── event/                       ← Domain Events (records implementing DomainEvent)
│   ├── ports/                       ← Write-side Repository interfaces (domain objects use domain types, not raw UUID/String; primitive types for pagination/filters are fine)
│   └── service/                     ← Domain Services (stateless, no I/O, no @Transactional)
│
├── application/                     ← @Service, Lombok OK. No JPA/Redis/Kafka imports.
│   ├── command/
│   │   └── {aggregate}/             ← package-by-feature
│   │       ├── {Action}{Agg}Command.java
│   │       ├── {Action}{Agg}CommandHandler.java
│   │       └── {Action}{Agg}Result.java
│   ├── query/
│   │   └── {aggregate}/             ← package-by-feature
│   │       ├── {Criteria}{Agg}Query.java
│   │       ├── {Criteria}{Agg}QueryHandler.java
│   │       └── {Agg}{Purpose}Result.java
│   └── port/
│       └── outbound/                ← Secondary ports that are NOT domain concepts
│           ├── {Agg}SearchRepository.java   ← ES read model
│           ├── {Agg}ReadRepository.java     ← JPA read projection (fallback)
│           ├── {Agg}Cache.java              ← Redis (if applicable)
│           └── {Target}Client.java          ← HTTP cross-service calls
│
├── infrastructure/                  ← All framework code lives here. @Transactional lives here.
│   ├── repository/
│   │   ├── jpa/
│   │   │   ├── {Agg}JpaEntity.java          ← @Entity; must not leave this package
│   │   │   ├── {Agg}JpaRepository.java      ← Spring Data interface
│   │   │   └── {Agg}PersistenceAdapter.java ← implements domain port(s); @Transactional here
│   │   └── elasticsearch/           ← order only
│   │       ├── {Agg}ElasticDocument.java
│   │       ├── {Agg}ElasticRepository.java
│   │       └── {Agg}SearchAdapter.java
│   ├── messaging/
│   │   └── outbox/
│   │       └── {Service}OutboxMapper.java   ← maps domain events → Avro OutboxEntry
│   ├── cache/                       ← catalog only
│   │   └── Redis{Agg}Cache.java
│   ├── client/                      ← order only (HTTP client to catalog)
│   │   └── {Target}RestClient.java
│   └── email/                       ← notification only
│       └── LogEmailAdapter.java
│
└── interfaces/                      ← Primary (driving) adapters. No business logic.
    ├── rest/
    │   ├── {Agg}CommandController.java
    │   ├── {Agg}QueryController.java
    │   ├── request/                 ← HTTP request body DTOs (one record per endpoint with a body)
    │   │   └── {Action}{Agg}Request.java
    │   └── response/                ← HTTP response DTOs (map from *Result in controller)
    │       └── {Agg}{Purpose}Response.java
    ├── messaging/
    │   └── consumer/                ← Only in services that consume Kafka events
    │       ├── {Topic}EventConsumer.java    ← single @KafkaListener entry point
    │       └── {Event}Handler.java          ← per-event handler, dispatches to CommandBus
    └── event/                       ← Same-service domain event listeners with business logic
        └── {Event}{Reaction}Listener.java  ← dispatches Command via CommandBus
```

### 3.2 Actual Service Structures

**catalog** (port 8081 | PostgreSQL + Redis):

```
domain/model/    Book, BookId, Author, Title, Category, Money, StockLevel, InsufficientStockException
domain/event/    BookAdded, StockReserved, StockReleased
domain/ports/    BookPersistence
application/command/book/    AddBook, UpdateBook, ReserveStock, ReleaseStock (+ Results)
application/query/book/      GetBook, ListBooks, GetStock (+ Responses)
application/port/outbound/   BookCache
infrastructure/repository/jpa/   BookJpaEntity, BookJpaRepository, BookPersistenceAdapter, BookEntityMapper
infrastructure/cache/        RedisBookCache, BookCacheInvalidationListener, BookPersistedEvent
infrastructure/messaging/outbox/ CatalogOutboxMapper
interfaces/rest/             BookCommandController, BookQueryController
interfaces/messaging/consumer/   OrderEventConsumer, OrderCancelledHandler
```

**order** (port 8082 | PostgreSQL + ElasticSearch — CQRS):

```
domain/model/    Order, OrderId, OrderItem, CustomerId, Money, PricingResult, OrderStatus, OrderStateException, InsufficientStockException
domain/event/    OrderPlaced, OrderConfirmed, OrderShipped, OrderCancelled
domain/ports/    OrderPersistence
domain/service/  OrderPricingService
application/command/order/   PlaceOrder, CancelOrder, AutoConfirmOrder (+ PlaceOrderResult)
application/query/order/     GetOrder, ListOrders (+ OrderDetailResult, OrderItemResult, OrderSummaryResult)
application/port/outbound/   CatalogClient, StockAvailability, OrderSearchRepository, OrderReadRepository
infrastructure/repository/jpa/         OrderJpaEntity, OrderJpaRepository, OrderPersistenceAdapter
infrastructure/repository/elasticsearch/ OrderElasticDocument, OrderElasticRepository, OrderSearchAdapter
infrastructure/messaging/outbox/        OrderOutboxMapper
infrastructure/client/                  CatalogRestClient
interfaces/rest/             OrderCommandController, OrderQueryController
interfaces/rest/request/     PlaceOrderRequest, CancelOrderRequest
interfaces/rest/response/    PlaceOrderResponse, OrderDetailResponse, OrderSummaryResponse
interfaces/event/            OrderPlacedAutoConfirmListener

**notification** (port 8083 | PostgreSQL):

```
domain/model/    Notification, NotificationId, Channel, DeliveryStatus, Payload
domain/event/    NotificationSent, NotificationFailed
domain/ports/    NotificationRepository
application/command/notification/  SendNotification
application/query/notification/    ListNotifications (+ NotificationResponse)
application/port/outbound/         CustomerClient, EmailSender
infrastructure/repository/jpa/     NotificationJpaEntity, NotificationJpaRepository, NotificationPersistenceAdapter
infrastructure/client/customer/    StubCustomerClient
infrastructure/client/email/       LogEmailAdapter
interfaces/rest/                   NotificationController
interfaces/messaging/consumer/     OrderEventConsumer, OrderPlacedHandler, OrderConfirmedHandler, OrderShippedHandler, OrderCancelledHandler
```

### 3.3 Layer Rules Enforcement

**`domain/` rules:**
- Zero imports from `application/`, `infrastructure/`, or any framework.
- `@Component`/`@Service` on Domain Services is acceptable (discovery-only annotation).
- No `@Entity`, `@Transactional`, or JPA relationship annotations.
- All Value Objects → Java `record` (immutable, structural equality).
- All Domain Events → Java `record` implementing `DomainEvent`.

**`application/` rules:**
- No JPA, Redis, Kafka, or framework I/O imports.
- `@Service` on Handlers is acceptable.
- Lombok (`@Slf4j`, `@RequiredArgsConstructor`) is acceptable.
- No `@Transactional` — it belongs in the infrastructure persistence adapter.
- No direct injection of concrete Handler classes — always via `CommandBus`/`QueryBus`.
- Command Handlers return `{Action}{Agg}Result` records, **never** Query-layer DTOs.

**`infrastructure/` rules:**
- Only layer that may import JPA, Redis, Kafka, or Elasticsearch SDKs.
- JPA Entities (`*JpaEntity`) must not be exposed outside their package — the mapper converts to/from domain objects.
- `@Transactional` annotations belong on Persistence Adapter methods, not on Handlers or Domain Services.
- One Persistence Adapter may implement multiple ports (e.g., `OrderPersistenceAdapter` implements both `OrderPersistence` from `domain/ports/` and `OrderReadRepository` from `application/port/outbound/`).

**`interfaces/` rules:**
- No business logic in Controllers.
- Controllers map HTTP input → Command/Query → `commandBus.dispatch()` / `queryBus.dispatch()`.
- Kafka Consumers deserialise → check idempotency → dispatch to CommandBus.
- **HTTP request body DTOs**: if the endpoint has a `@RequestBody`, define a `{Action}{Agg}Request` record in `interfaces/dto/`. Endpoints with only path variables or query params need no request object.

---

## Part IV: CQRS Implementation

### 4.1 Scope

CQRS (physically separated read and write storage) is applied **exclusively to `order`** (see [ADR-002](adr/ADR-002-cqrs-scope-order-service.md)).

- `catalog`: single PostgreSQL with Redis cache layer — no CQRS overhead justified.
- `notification`: insert-only log, simple reads — no CQRS needed.
- `order`: complex queries (by customer, date range, status, full-text) against high-frequency write workload — CQRS justified.

### 4.2 Command Flow (Write Path)

```
HTTP POST /api/v1/orders
  ↓
OrderCommandController                    [interfaces/rest/]
  commandBus.dispatch(PlaceOrderCommand)
  ↓
PlaceOrderCommandHandler                  [application/command/order/]
  1. CatalogClient.checkStock()           ← HTTP call (outside transaction)
  2. CatalogClient.reserveStock()         ← HTTP call (outside transaction)
  3. OrderPricingService.calculate()      ← Domain Service (pure, in-memory)
  4. Order.create(items, total)           ← Aggregate factory
  5. order.place()                        ← registers OrderPlaced domain event (in-memory)
  6. OrderPersistence.save(order)         ← @Transactional opens here
       ↓
       BookEntityMapper attaches domain events to AbstractAggregateRootEntity
       OutboxWriteListener (BEFORE_COMMIT) writes outbox_event row atomically
       @Transactional commits
  ↓
OutboxRelayScheduler                      [seedwork / infrastructure]
  publishes OrderPlaced (Avro) to Kafka topic bookstore.order.placed
  ↓
returns PlaceOrderResult                  ← assembled from in-memory domain state; zero extra I/O
```

### 4.3 Query Flow (Read Path)

```
HTTP GET /api/v1/orders/{id}
  ↓
OrderQueryController                      [interfaces/rest/]
  queryBus.dispatch(GetOrderQuery)
  ↓
GetOrderQueryHandler                      [application/query/order/]
  1. OrderSearchRepository.findById()     ← Elasticsearch (primary)
     OR OrderReadRepository.findDetailById() ← JPA projection (fallback if ES unavailable)
  returns OrderDetailResult            ← flat DTO; domain layer never loaded
```

The read path **never loads domain entities**. It returns DTOs directly from ES documents or JPA projections.

### 4.4 Command Result vs Query Result

These are independent types and must not be merged:

| | Type | Location | Assembled from |
|--|------|----------|----------------|
| **Command result** | `PlaceOrderResult` | `application/command/order/` | In-memory domain state after `save()` — **zero extra I/O** |
| **Query result** | `OrderDetailResult` | `application/query/order/` | ES document or JPA projection — may require a DB/ES read |

`PlaceOrderResult` and `OrderDetailResult` may share similar fields but are separate records with separate purposes. Coupling them would create a bidirectional dependency between the write and read sides.

---

## Part V: Event-Driven Architecture

### 5.1 Domain Events (in-process)

Domain Events are pure Java `record`s in `domain/event/`, implementing `DomainEvent` from seedwork.

```java
// domain/event/OrderPlaced.java
public record OrderPlaced(
    UUID eventId,
    UUID orderId,
    UUID customerId,
    List<OrderItem> items,
    BigDecimal totalAmount,
    Instant occurredAt
) implements DomainEvent {}
```

They are registered in-memory on the Aggregate via `registerDomainEvent(event)` (from `AggregateRoot`) and are never dispatched directly by handlers.

### 5.2 Integration Events (cross-service)

Integration Events are Avro schemas in `shared-events/src/main/avro/com/example/events/v1/`. The build generates `SpecificRecord` Java classes published as the `shared-events` library.

| Event | Producer | Consumers |
|-------|----------|-----------|
| `OrderPlaced` | order | notification |
| `OrderConfirmed` | order | notification |
| `OrderShipped` | order | notification |
| `OrderCancelled` | order | notification, catalog |
| `StockReserved` | catalog | order |
| `StockReleased` | catalog | — |

### 5.3 Outbox Pattern — Publishing Flow

```
Aggregate.someAction()
    registers DomainEvent internally
    ↓
{Agg}PersistenceAdapter.save(aggregate)   ← @Transactional
    mapper.toNewEntity(aggregate)
      → BookEntityMapper / OrderEntityMapper attaches domain events
        to AbstractAggregateRootEntity via attachDomainEvents()
    jpaRepository.save(entity)
      → Spring Data @DomainEvents fires events to context
        → OutboxWriteListener.beforeCommit(event)
             calls OutboxMapper.toOutboxEntry(domainEvent)
             writes OutboxEventJpaEntity to outbox_event table
    @Transactional commits (aggregate + outbox row in same ACID transaction)
    ↓
OutboxRelayScheduler
    reads PENDING outbox rows
    publishes Avro bytes to Kafka
    marks rows PUBLISHED
```

Key invariant: **the aggregate state change and the outbox row are always in the same ACID transaction**. There is no window where one exists without the other.

### 5.4 OutboxMapper SPI

Each service implements exactly one `OutboxMapper` (in `infrastructure/messaging/outbox/`):

```java
// seedwork OutboxMapper SPI
public interface OutboxMapper {
    Optional<OutboxEntry> toOutboxEntry(DomainEvent event);
}

// CatalogOutboxMapper — pattern matching on event type
@Component
public class CatalogOutboxMapper implements OutboxMapper {
    @Override
    public Optional<OutboxEntry> toOutboxEntry(DomainEvent event) {
        return switch (event) {
            case StockReserved e -> {
                var avro = com.example.events.v1.StockReserved.newBuilder()
                        .setEventId(e.eventId().toString())
                        // ... field mapping
                        .build();
                yield Optional.of(new OutboxEntry(
                        e.eventId(), e.bookId(),
                        KafkaResourceConstants.TOPIC_STOCK_RESERVED,
                        avro.getClass().getName(),
                        avroSerializer.serialize(topic, avro)));
            }
            default -> Optional.empty();
        };
    }
}
```

### 5.5 Kafka Consumer Idempotency

All Kafka consumers must be idempotent (messages may be delivered more than once). The seedwork infrastructure handles this transparently:

```
KafkaMessageProcessor.process(handler, record, ack)
    ├── SELECT FROM processed_events WHERE id = eventId
    │   └── found → ack, return (duplicate — skip)
    │   └── not found →
    │       handler.handle(event)           ← business logic
    │       INSERT INTO processed_events    ← mark processed
    │       ack                             ← commit offset
    │
    └── on handler failure →
        INSERT INTO consumer_retry_events   ← store for retry
        ack                                 ← unblock the Kafka partition
```

Retry uses **DB-backed exponential backoff** with a claim pattern that prevents duplicate processing across multiple service instances. See [ADR-009](adr/ADR-009-kafka-consumer-idempotency-retry.md) for full design.

---

## Part VI: Transaction Rules

### 6.1 Default: Transaction on the Persistence Adapter

For the common case — one Handler, one aggregate — `@Transactional` lives on the Persistence Adapter's `save()` method:

```
PlaceOrderCommandHandler.handle():
  ① CatalogClient.checkStock()       ← no transaction
  ② CatalogClient.reserveStock()     ← no transaction
  ③ Order.create() + order.place()   ← in-memory, no transaction
  ④ OrderPersistence.save(order)     ← @Transactional opens and commits here
                                        (includes outbox write in same TX)
  ⑤ return PlaceOrderResult          ← assembled from in-memory state, no transaction
```

### 6.2 Post-Commit Side Effects — Decision Matrix

| Side effect type | Mechanism | Example |
|-----------------|-----------|---------|
| **Primary purpose of the command** | Direct call in Handler after `save()` | `SendNotificationCommandHandler` calls `EmailSender.send()` |
| **Reaction, same service, single consumer** | `@TransactionalEventListener(AFTER_COMMIT)` | Cache invalidation after book update |
| **Reaction, same service, multiple consumers** | Domain Event + `@TransactionalEventListener` | Multiple things respond to order state change |
| **Cross-service reaction** | Domain Event → Integration Event via Outbox (mandatory) | `OrderCancelled` → catalog releases stock |
| **Infrastructure operation (cache, search index)** | Inside Persistence Adapter `save()` | Cache eviction — Handler is unaware |

**Cache invalidation pattern (catalog):**

The `BookPersistenceAdapter.save()` publishes a `BookPersistedEvent` (a Spring application event, not a domain event) immediately after the JPA save. `BookCacheInvalidationListener` handles it with `@TransactionalEventListener(phase = AFTER_COMMIT)`, ensuring cache eviction only happens after the DB commit is confirmed.

```java
// inside BookPersistenceAdapter.save()
BookJpaEntity saved = bookJpaRepository.save(entity);
eventPublisher.publishEvent(new BookPersistedEvent(book.getId().value()));

// BookCacheInvalidationListener
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onBookPersisted(BookPersistedEvent event) {
    bookCache.evict(event.bookId());
}
```

### 6.3 Same-Service Domain Event Listener Classification

Same-service domain event listeners serve two fundamentally different architectural roles. Placing them correctly requires understanding the Hexagonal Architecture distinction between driving and driven adapters.

**Infrastructure listeners (driven adapters)** perform pure technical I/O — data synchronization with no business logic. They live in `infrastructure/`:

| Listener | Location | What it does |
|----------|----------|--------------|
| `OutboxWriteListener` | `seedwork/.../infrastructure/outbox/` | Serializes domain event → writes outbox row (BEFORE_COMMIT) |
| `BookCacheInvalidationListener` | `catalog/.../infrastructure/cache/` | Invalidates Redis cache entry (AFTER_COMMIT) |

Note: ES read model sync in the order service is handled externally by the Debezium CDC → ES Sink Connector pipeline, not by an in-application listener.

**Business event listeners (driving adapters)** translate domain events into Commands dispatched via CommandBus. They contain zero business logic — the decision-making lives in the CommandHandler and Domain Model. They are structurally identical to REST controllers and Kafka consumers:

| Driving adapter | Trigger source | Action |
|----------------|---------------|--------|
| REST Controller | HTTP request | `commandBus.dispatch(Command)` |
| Kafka Consumer | Kafka message | `commandBus.dispatch(Command)` |
| **Event Listener** | **Domain Event** | `commandBus.dispatch(Command)` |

Business event listeners live in `interfaces/event/`:

| Listener | Location | What it dispatches |
|----------|----------|-------------------|
| `OrderPlacedAutoConfirmListener` | `order/.../interfaces/event/` | `AutoConfirmOrderCommand` via CommandBus |

**Classification test:** Does the listener drive the application layer to execute business logic, or does it perform a technical I/O operation itself?
- Drives application → `interfaces/event/` (driving adapter)
- Performs I/O → `infrastructure/` (driven adapter)

### 6.4 Never Wrap External I/O in a Transaction

Holding a DB connection open across an HTTP call or email send:
- Exhausts the connection pool under load.
- Makes rollback semantically incorrect (a rollback cannot undo a sent email or an HTTP call that succeeded).

`@Transactional` on `save()` means the transaction is already committed before the Handler makes any post-save calls.

### 6.5 Never on Domain Services

`@Transactional` on a Domain Service creates a Spring proxy and adds a hidden dependency on `PlatformTransactionManager`. The Domain Service can no longer be tested without a Spring context.

### 6.6 Cross-Aggregate Atomicity (Same Database)

If a single use case must save two aggregate roots atomically to the same DB, place `@Transactional` on the CommandHandler. This is an architectural exception — document it in an ADR. First verify that the two aggregates are not actually one.

### 6.7 Cross-Service Atomicity

Impossible at the DB level. Use the Saga pattern: each step is an independent atomic commit; failures are handled by compensating transactions published as events.

```
PlaceOrderHandler:
  save Order(PENDING) + outbox(OrderPlaced)     ← atomic commit

catalog consumes OrderPlaced:
  reserve stock → emit StockReserved or StockReservationFailed

order consumes StockReserved:
  confirm order                                 ← atomic commit

order consumes StockReservationFailed:
  cancel order                                  ← compensating transaction
```

---

## Part VII: Naming Conventions

| Concept | Location | Pattern | Example |
|---------|----------|---------|---------|
| Command | `application/command/{agg}/` | `{Action}{Agg}Command` | `PlaceOrderCommand` |
| Command Handler | `application/command/{agg}/` | `{Action}{Agg}CommandHandler` | `PlaceOrderCommandHandler` |
| Command Result | `application/command/{agg}/` | `{Action}{Agg}Result` | `PlaceOrderResult` |
| Query | `application/query/{agg}/` | `{Criteria}{Agg}Query` | `GetOrderQuery`, `ListOrdersQuery` |
| Query Handler | `application/query/{agg}/` | `{Criteria}{Agg}QueryHandler` | `GetOrderQueryHandler` |
| Read Model Result | `application/query/{agg}/` | `{Agg}{Purpose}Result` | `OrderDetailResult`, `OrderSummaryResult` |
| Write-side Port | `domain/ports/` | `{Agg}Persistence` | `OrderPersistence`, `BookPersistence` |
| Read-side Port | `application/port/outbound/` | `{Agg}SearchRepository` | `OrderSearchRepository` |
| Read fallback Port | `application/port/outbound/` | `{Agg}ReadRepository` | `OrderReadRepository` |
| Cache Port | `application/port/outbound/` | `{Agg}Cache` | `BookCache` |
| HTTP Client Port | `application/port/outbound/` | `{Target}Client` | `CatalogClient` |
| Domain Event | `domain/event/` | `{Agg}{PastTense}` | `OrderPlaced`, `StockReserved` |
| JPA Entity | `infrastructure/repository/jpa/` | `{Agg}JpaEntity` | `OrderJpaEntity` |
| JPA Repository | `infrastructure/repository/jpa/` | `{Agg}JpaRepository` | `OrderJpaRepository` |
| Persistence Adapter | `infrastructure/repository/jpa/` | `{Agg}PersistenceAdapter` | `BookPersistenceAdapter` |
| Entity Mapper | `infrastructure/repository/jpa/` | `{Agg}EntityMapper` | `BookEntityMapper` |
| ES Document | `infrastructure/repository/elasticsearch/` | `{Agg}ElasticDocument` | `OrderElasticDocument` |
| ES Repository | `infrastructure/repository/elasticsearch/` | `{Agg}ElasticRepository` | `OrderElasticRepository` |
| ES Adapter | `infrastructure/repository/elasticsearch/` | `{Agg}SearchAdapter` | `OrderSearchAdapter` |
| Outbox Mapper | `infrastructure/messaging/outbox/` | `{Service}OutboxMapper` | `CatalogOutboxMapper` |
| HTTP Client Adapter | `infrastructure/client/` | `{Target}RestClient` | `CatalogRestClient` |
| Cache Adapter | `infrastructure/cache/` | `{Technology}{Agg}Cache` | `RedisBookCache` |
| REST Controller (write) | `interfaces/rest/` | `{Agg}CommandController` | `OrderCommandController` |
| REST Controller (read) | `interfaces/rest/` | `{Agg}QueryController` | `OrderQueryController` |
| HTTP Request DTO | `interfaces/rest/request/` | `{Action}{Agg}Request` | `PlaceOrderRequest` |
| HTTP Response DTO | `interfaces/rest/response/` | `{Agg}{Purpose}Response` | `OrderDetailResponse`, `PlaceOrderResponse` |
| Kafka Consumer | `interfaces/messaging/consumer/` | `{Topic}EventConsumer` | `OrderEventConsumer` |
| Kafka Event Handler | `interfaces/messaging/consumer/` | `{Event}Handler` | `OrderPlacedHandler` |
| Same-service event listener (business) | `interfaces/event/` | `{Event}{Reaction}Listener` | `OrderPlacedAutoConfirmListener` |
| Same-service event listener (technical) | `infrastructure/{concern}/` | `{Aggregate}{Concern}Listener` | `BookCacheInvalidationListener` |

---

## Part VIII: Testing Strategy

```
Unit tests      → Domain layer + Application layer (no Spring context, no Docker)
Integration     → Each adapter in isolation (Testcontainers: Postgres, Redis, Kafka, ES)
Component       → Full service in-memory (SpringBootTest + Testcontainers)
Contract        → REST API BDCT via PactFlow (order-service → catalog-service)
E2E             → REST calls against pre-deployed environment (no infra setup in CI)
```

### 8.1 By Layer

| Zone | Test Style | Setup | Speed |
|------|-----------|-------|-------|
| Domain Model | JUnit 5, no mocks — test via behavior | None | < 1ms |
| Domain Services | JUnit 5, pass in-memory entities | None | < 1ms |
| Command/Query Handlers | JUnit 5 + Mockito for port mocks | Mockito only | < 100ms |
| Persistence Adapters | `@DataJpaTest` + Testcontainers PostgreSQL | Docker | < 1s |
| REST Controllers | `@WebMvcTest` + MockMvc (mock bus) | Spring slice | < 500ms |
| Kafka Consumers | Testcontainers Kafka | Docker | < 2s |
| Full service | `@SpringBootTest` + all Testcontainers | Docker | < 10s |

### 8.2 Domain Tests — Zero Mocks

```java
// No Spring, no Mockito, no Docker
@Test
void order_cannot_be_cancelled_after_shipping() {
    var order = Order.create(customerId, items);
    order.place();
    order.confirm();
    order.ship("TRACK-123");

    assertThatThrownBy(() -> order.cancel("changed mind"))
        .isInstanceOf(OrderStateException.class);
}
```

### 8.3 Handler Tests — Mock Ports Only

```java
// No Spring context, no Docker
@ExtendWith(MockitoExtension.class)
class PlaceOrderCommandHandlerTest {
    @Mock OrderPersistence orderPersistence;
    @Mock CatalogClient catalogClient;
    @InjectMocks PlaceOrderCommandHandler handler;

    @Test
    void places_order_successfully() { ... }
}
```

### 8.4 Contract Testing (BDCT)

Scope: `order-service` (Consumer) → `catalog-service` (Provider)

- Consumer writes Pact tests → generates `build/pacts/order-service-catalog-service.json` → publishes to PactFlow.
- Provider publishes OAS spec (generated by springdoc-openapi from actual `@RestController` annotations) → PactFlow cross-validates.
- No provider-side replay tests. PactFlow `can-i-deploy` gate blocks incompatible deployments.

```bash
# Run consumer contract tests locally (no Testcontainers required)
cd order && ./gradlew test --tests "com.example.order.contract.*"
```

See [ADR-011](adr/ADR-011-swaggerhub-pactflow-bdct.md) for full CI workflow.

---

## Part IX: Anti-Patterns

### Domain Model

| Anti-Pattern | Symptom | Fix |
|-------------|---------|-----|
| `@Entity` on domain aggregate | Aggregate has JPA annotations | Create a separate `*JpaEntity` in `infrastructure/`; map explicitly |
| `@Transactional` on Domain Service | Domain Service cannot be tested without Spring | Remove; transaction scope belongs in the Handler or Adapter |
| Repository injected into Domain Service | Domain Service constructor takes a Port | Move the fetch to the Handler; pass entities as parameters |
| Business logic in JPA entity | `@Entity` class contains `if/else` business rules | Move to Aggregate; JPA entity is a persistence-only mapping structure |

### Application Layer

| Anti-Pattern | Symptom | Fix |
|-------------|---------|-----|
| Handler returns Query DTO | `PlaceOrderCommandHandler` returns `OrderDetailResponse` | Return `PlaceOrderResult` assembled from in-memory domain state |
| Business logic in Handler | Handler contains `if (businessCondition) throw …` | Move the check to a domain method or Domain Service |
| Direct Handler injection in Controller | `@Autowired PlaceOrderCommandHandler handler` | Dispatch via `CommandBus` only |
| JPA/Kafka import in application package | `import jakarta.persistence.*` in a handler | Move any persistence/messaging code to `infrastructure/` |
| Application Service alongside Handlers | Classes named `*Service` in `application/` (not Domain Services) | Consolidate into Handlers; only Domain Services use `*Service` naming |

### Infrastructure

| Anti-Pattern | Symptom | Fix |
|-------------|---------|-----|
| JPA Entity exposed outside `jpa/` package | Handler or Controller references `*JpaEntity` | Map to domain object inside the adapter; use package-private class |
| `@Transactional` on Handler when Adapter has it | Double transaction management | Remove from Handler; keep on Adapter `save()` |
| External I/O inside transaction | HTTP call or email send while DB connection is held | Move external calls before or after the `save()` call |
| Cache management in Handler | Handler calls `bookCache.put()` | Move cache put/evict into Persistence Adapter's `save()` |
| EventDispatcher called in Handler | `eventPublisher.publish(new OrderPlaced(…))` in handler | Remove; publishing is a side-effect of `persistence.save()` via `OutboxWriteListener` |

### Events & Cross-Service

| Anti-Pattern | Symptom | Fix |
|-------------|---------|-----|
| Domain event imports Avro class | `domain/event/OrderPlaced.java` imports `com.example.events.v1.*` | Domain events are pure Java records; only `OutboxMapper` imports Avro |
| HTTP call for cross-service state change | `catalogClient.releaseStock()` called after order cancel | Use `OrderCancelled` integration event; catalog consumes and releases idempotently |
| Shared DB tables across services | Two services query the same table | Each service owns its DB; cross-service data flows through Kafka events |
| Write-side port in `application/port/outbound/` | `OrderPersistence` placed alongside `CatalogClient` | Write-side ports speak domain language → `domain/ports/`; only infrastructure abstractions go in `application/port/outbound/` |
| Consumer without idempotency check | Kafka listener has no deduplication | Use `KafkaMessageProcessor` or `IdempotentKafkaListener` from seedwork |

---

## Part X: Technology Decisions (ADR Index)

| ADR | Decision | Rationale |
|-----|----------|-----------|
| [ADR-001](adr/ADR-001-explicit-architecture-over-layered.md) | Adopt Explicit Architecture over traditional layered | Testability, enforced boundaries, framework independence |
| [ADR-002](adr/ADR-002-cqrs-scope-order-service.md) | CQRS applied only to `order` (PostgreSQL write + Elasticsearch read) | Order has complex read patterns; catalog and notification do not justify the overhead |
| [ADR-003](adr/ADR-003-event-schema-ownership.md) | `shared-events` as Avro SDK (schema → generated classes → mavenLocal) | Compile-time contract; Schema Registry enforces backward compatibility |
| [ADR-005](adr/ADR-005-outbox-pattern.md) | Outbox Pattern for guaranteed event delivery | Atomic aggregate + event row in same ACID TX; no distributed transaction needed |
| [ADR-006](adr/ADR-006-database-per-service.md) | Database-per-service, no shared tables | Bounded context isolation; independent deployment and scaling |
| [ADR-007](adr/ADR-007-java21-virtual-threads.md) | Java 21 + Virtual Threads + modern language features | Records for VOs/events/DTOs; sealed interfaces for domain enums; pattern matching in domain logic |
| [ADR-008](adr/ADR-008-shared-events-versioning.md) | SemVer for `shared-events`; BACKWARD-only schema changes; `v2/` namespace for breaking changes | Prevents consumers from being silently broken by schema evolution |
| [ADR-009](adr/ADR-009-kafka-consumer-idempotency-retry.md) | DB-backed idempotency (`processed_events`) + claim-pattern retry (`consumer_retry_events`) | Never blocks Kafka partition; survives pod restarts; distributed-safe without external lock manager |
| [ADR-010](adr/ADR-010-opentelemetry-observability.md) | OpenTelemetry via Kubernetes operator for traces + Prometheus for metrics | Uniform observability without per-service agent config |
| [ADR-011](adr/ADR-011-swaggerhub-pactflow-bdct.md) | SwaggerHub (API registry) + PactFlow BDCT (contract testing) | Provider team never blocked by consumer pact state; OAS generated from actual controller code |

### Module Build Order

```bash
# Always build in this order (each step publishes to mavenLocal)
cd seedwork      && ./gradlew publishToMavenLocal
cd shared-events && ./gradlew publishToMavenLocal
# Now build any service
cd catalog / order / notification
```

### Key Technology Stack

| Concern | Technology |
|---------|-----------|
| Language | Java 21 (preview features enabled) |
| Framework | Spring Boot 3 |
| Build | Gradle (independent project per service, no multi-project root) |
| Container images | Jib (no Dockerfile; base: `eclipse-temurin:21-jre-alpine`) |
| Write DB | PostgreSQL (per service) |
| Cache | Redis (catalog only) |
| Search | Elasticsearch (order read model) |
| Messaging | Kafka + Confluent Avro + Schema Registry |
| Event relay | Debezium CDC (production) / `OutboxRelayScheduler` (local dev) |
| Observability | OpenTelemetry + Prometheus + Jaeger + Grafana |
| Service mesh | Istio (mTLS, ingress) |
| Email | `LogEmailAdapter` (log simulation; no SMTP) |
| Virtual threads | `spring.threads.virtual.enabled=true` |

---

## Appendix: Adding a New Feature (Checklist)

1. Define domain model change in `domain/model/`
2. Add/update domain event in `domain/event/`
3. If persistence needed: define or update write-side port in `domain/ports/`
4. If external client/cache/search needed: define port in `application/port/outbound/`
5. Add `{Action}{Agg}Command` + `{Action}{Agg}Result` + `@Service` CommandHandler in `application/command/{agg}/`
6. Add `{Criteria}{Agg}Query` + `@Service` QueryHandler + Response DTO in `application/query/{agg}/`
7. Implement Persistence Adapter in `infrastructure/repository/jpa/` — include cache invalidation here
8. Add REST endpoint in `interfaces/rest/` — dispatch via `CommandBus`/`QueryBus`
9. Add Flyway migration if schema changes
10. Write domain behavior tests (JUnit 5, no mocks)
11. Write handler unit tests (Mockito mocks for ports)
12. Write persistence adapter integration test (Testcontainers)
