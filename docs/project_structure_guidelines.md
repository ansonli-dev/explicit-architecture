# Project Structure Guidelines

This document uses **order-service** as an example to explain the code organisation conventions for
**DDD + Hexagonal (Ports & Adapters) + CQRS**.

> The same conventions apply to all three services — `catalog`, `order`, and `notification`.
> Only the `infrastructure/` adapters are trimmed to each service's actual technology stack.

---

## Core Principles

1. **Dependency direction**: `interfaces/` and `infrastructure/` depend on `application/`; `application/` depends on `domain/`. Reverse dependencies are strictly forbidden.
2. **Domain purity**: `domain/` must not contain any framework annotations or I/O operations — pure Java only.
3. **CQRS separation**: `command/` (write operations) and `query/` (read operations) are strictly separated at the application layer, each with its own independent Handler.
4. **Package by feature**: Within `application/command/` and `application/query/`, organise packages vertically by aggregate (e.g., `order`) rather than laying all Commands/Queries flat.
5. **Adapter sides**: REST Controllers and Kafka Consumers (driving side) go in `interfaces/`; JPA, Redis, Kafka Producers, and HTTP Clients (driven side) go in `infrastructure/`.
6. **CommandBus / QueryBus as driving-side entry points**: Controllers depend only on the `CommandBus` and `QueryBus` interfaces — they never inject concrete Handler classes, and no per-use-case inbound port interfaces are defined.

---

## Directory Overview (order-service)

```text
com.example.order/
├── domain/              # Zero framework dependencies — pure Java domain model
├── application/         # Use case orchestration — depends only on domain
├── infrastructure/      # Driven adapters — JPA, Outbox, ElasticSearch, HTTP Client
└── interfaces/          # Driving adapters — REST Controller, Kafka Consumer
```

---

## Layer Details

### 1. `domain/` — Domain Layer (innermost)

**Responsibility**: The single source of truth for business rules. Contains aggregates, entities, value objects, domain events, and domain services.

**Rules**:
- Must not depend on any class from `application/` or `infrastructure/`.
- Repository interfaces are not defined here (see `application/port/outbound/`).
- Zero framework annotations — `@Component`, `@Entity`, `@Transactional`, and any other annotation are not allowed.

```text
domain/
├── model/
│   ├── Order.java              # Aggregate root: encapsulates order state machine and invariants
│   ├── OrderItem.java          # Entity: belongs to the Order aggregate, snapshots title and unit price
│   ├── OrderId.java            # Value object (record)
│   ├── CustomerId.java         # Value object (record)
│   ├── Money.java              # Value object (record): amount + currency, immutable
│   ├── OrderStatus.java        # Sealed interface: Pending / Confirmed / Shipped / Cancelled
│   ├── PricingResult.java      # Value object (record): result from the pricing service
│   └── OrderStateException.java # Domain exception: illegal state transition
├── event/
│   ├── OrderPlaced.java        # Domain event (record): order has been placed
│   ├── OrderConfirmed.java     # Domain event (record): order has been confirmed
│   ├── OrderShipped.java       # Domain event (record): order has been shipped
│   └── OrderCancelled.java     # Domain event (record): order has been cancelled
└── service/
    └── OrderPricingService.java # Domain service: discount calculation logic across OrderItems
```

> **When to use a Domain Service**: when the operation is stateless, does not naturally belong to a single aggregate, and requires collaboration across multiple domain objects (e.g., pricing discount needs to iterate all `OrderItem`s).

---

### 2. `application/` — Application Layer

**Responsibility**: Use case orchestration — calls domain objects and depends on external capabilities through port interfaces.

**Rules**:
- Must not import Spring, JPA, Kafka, or Redis framework classes (`@Service` and Lombok are acceptable).
- CommandHandlers / QueryHandlers are annotated with `@Service` and wired automatically by Spring.
- `@Transactional` does not go on Handlers — it belongs in the `infrastructure/repository/jpa/` adapter.
- Controllers call Handlers via `CommandBus` / `QueryBus` — no direct injection of concrete Handler classes, and no per-use-case inbound port interfaces are defined.

```text
application/
├── port/
│   └── outbound/                       # Secondary ports: declarations of this service's external dependencies
│       ├── OrderPersistence.java       # → JPA implementation (infrastructure/repository/jpa/)
│       ├── OrderSearchRepository.java  # → ElasticSearch implementation (infrastructure/repository/elasticsearch/)
│       └── CatalogClient.java          # → HTTP implementation (infrastructure/client/)
├── command/
│   └── order/                         # Packaged by aggregate
│       ├── PlaceOrderCommand.java      # Command record (write-side input; no HTTP details)
│       ├── PlaceOrderCommandHandler.java   # @Service, implements CommandHandler<PlaceOrderCommand, OrderId>
│       ├── CancelOrderCommand.java     # Command record
│       └── CancelOrderCommandHandler.java  # @Service, implements CommandHandler<CancelOrderCommand, Void>
└── query/
    └── order/                         # Packaged by aggregate
        ├── GetOrderQuery.java          # Query record: fetch single order by ID
        ├── GetOrderQueryHandler.java   # @Service, implements QueryHandler<GetOrderQuery, OrderDetailResponse>; queries ES directly
        ├── ListOrdersQuery.java        # Query record: pagination + filter criteria
        ├── ListOrdersQueryHandler.java # @Service, implements QueryHandler<ListOrdersQuery, List<OrderSummaryResponse>>
        ├── OrderDetailResponse.java    # Response DTO record (single order detail)
        ├── OrderItemResponse.java      # Response DTO record (order item)
        ├── OrderSummaryResponse.java   # Response DTO record (list summary)
        └── OrderNotFoundException.java # Application-layer exception: order not found
```

> **Difference between commands and queries**:
> - `CommandHandler`: mutates system state, writes to PostgreSQL, registers domain events (via Outbox — not called directly)
> - `QueryHandler`: read-only, queries the ElasticSearch read model directly, bypasses the domain layer entirely

---

### 3. `infrastructure/` — Infrastructure Layer (driven adapters)

**Responsibility**: Implement all secondary port interfaces defined in `application/port/outbound/`.

**Rules**:
- This is the only layer that may import JPA / Kafka / Redis / Elasticsearch SDKs.
- JPA Entities (`*JpaEntity`) must not be exposed outside this package — the Mapper handles conversion to and from domain objects.
- `@Transactional` annotations are used only in persistence adapters.
- The Outbox base table (`outbox_event`) along with its JPA entity, relay scheduler, and publisher are all provided by seedwork — do not redefine them in service code.

```text
infrastructure/
├── repository/
│   ├── jpa/
│   │   ├── OrderPersistenceAdapter.java  # Implements OrderPersistence (includes @Transactional)
│   │   ├── OrderJpaEntity.java           # JPA entity (@Entity); must not leave this package
│   │   ├── OrderItemJpaEntity.java       # JPA entity
│   │   └── OrderJpaRepository.java       # Spring Data JPA interface
│   └── elasticsearch/
│       ├── OrderSearchAdapter.java       # Implements OrderSearchRepository
│       ├── OrderElasticDocument.java     # ES document model
│       └── OrderElasticRepository.java   # Spring Data ES interface
├── messaging/
│   └── outbox/
│       └── OrderOutboxMapper.java        # Implements seedwork OutboxMapper SPI (domain event → Avro payload)
└── client/
    └── CatalogRestClient.java            # Implements CatalogClient (WebClient HTTP call)
```

---

### 4. `interfaces/` — Interfaces Layer (driving adapters)

**Responsibility**: System entry points — receive external requests (HTTP, Kafka messages), convert them to Commands / Queries, and dispatch via `CommandBus` / `QueryBus`.

**Rules**:
- Business logic must not be written in Controllers.
- Controllers map HTTP input to Command / Query objects, then call `commandBus.dispatch()` / `queryBus.dispatch()` — concrete Handler classes are never injected directly.
- Kafka Consumers deserialise messages into internal objects and similarly dispatch via CommandBus to the application layer.

```text
interfaces/
├── rest/
│   ├── OrderCommandController.java     # POST /api/v1/orders, PUT /api/v1/orders/{id}/cancel
│   └── OrderQueryController.java       # GET /api/v1/orders/{id}, GET /api/v1/orders
└── messaging/
    └── consumer/
        ├── OrderEventConsumer.java     # @IdempotentKafkaListener single entry point, routes to per-event handlers
        └── OrderPlacedHandler.java     # Handles OrderPlaced event; dispatches to CommandBus
```

---

## Core Execution Flow (Place Order)

```
HTTP POST /api/v1/orders
  ↓
OrderCommandController          [interfaces/rest/]
  → commandBus.dispatch(new PlaceOrderCommand(...))
  ↓
PlaceOrderCommandHandler        [application/command/order/]
  1. CatalogClient.checkStock() & reserveStock()
  2. OrderPricingService.calculate()            ← Domain Service [domain/service/]
  3. Order.create(items, finalTotal)             ← Aggregate [domain/model/]
  4. Order.place() → registers OrderPlaced      ← Domain Event (in memory) [domain/event/]
  5. OrderPersistence.save(order)               ← AbstractAggregateRootEntity carries events
  ↓
OutboxWriteListener (BEFORE_COMMIT)             [seedwork]
  → atomically writes outbox_event row in the same transaction
  ↓
Debezium CDC / OutboxRelayScheduler             [seedwork / infrastructure]
  → publishes to Kafka topic: bookstore.order.placed
```

---

## Naming Quick Reference

| Concept | Naming Pattern | Example |
|---|---|---|
| Command record | `{Action}{Aggregate}Command` | `PlaceOrderCommand` |
| CommandHandler | `{Action}{Aggregate}CommandHandler` | `PlaceOrderCommandHandler` |
| Query record | `{Criteria}{Aggregate}Query` | `GetOrderQuery`, `ListOrdersQuery` |
| QueryHandler | `{Criteria}{Aggregate}QueryHandler` | `ListOrdersQueryHandler` |
| Response DTO | `{Aggregate}{Purpose}Response` | `OrderDetailResponse` |
| Repository Port | `{Aggregate}Persistence` | `OrderPersistence` |
| Read Model Port | `{Aggregate}SearchRepository` | `OrderSearchRepository` |
| Service Client Port | `{Target}Client` | `CatalogClient` |
| Domain Event | `{Aggregate}{PastTense}` | `OrderPlaced` |
| JPA Entity | `{Aggregate}JpaEntity` | `OrderJpaEntity` |
| JPA Repository | `{Aggregate}JpaRepository` | `OrderJpaRepository` |
| ES Document | `{Aggregate}ElasticDocument` | `OrderElasticDocument` |
| ES Repository | `{Aggregate}ElasticRepository` | `OrderElasticRepository` |
| REST Controller (write) | `{Aggregate}CommandController` | `OrderCommandController` |
| REST Controller (read) | `{Aggregate}QueryController` | `OrderQueryController` |
| Outbox Mapper adapter | `{Service}OutboxMapper` | `OrderOutboxMapper` |
| Kafka Consumer adapter | `{Event}Consumer` | `OrderEventConsumer` |
| HTTP Client adapter | `{Target}RestClient` | `CatalogRestClient` |
