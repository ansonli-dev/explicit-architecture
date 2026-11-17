---
type: architecture
title: System boundaries and data ownership
description: Maps the bookstore services, their actual entrypoints and state owners, and the synchronous and asynchronous paths connecting them.
tags: [architecture, ownership, bookstore, cqrs]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-e938156c2974c3e6bf65c6a5
    resource: repo://catalog/src/main/java/com/example/catalog/interfaces/rest/BookCommandController.java
  - id: openwiki-source-342594298fb62993a735eefa
    resource: repo://catalog/src/main/resources/application.yml
  - id: openwiki-source-5c13b4721950d99b88333e05
    resource: repo://notification/src/main/java/com/example/notification/interfaces/rest/NotificationController.java
  - id: openwiki-source-74c57bc7db7612e8ad15fcbf
    resource: repo://notification/src/main/resources/application.yml
  - id: openwiki-source-20b3e9a6e4f23c4e001519ed
    resource: repo://order/src/main/java/com/example/order/application/command/order/PlaceOrderCommandHandler.java
  - id: openwiki-source-01c22f8628299fa3142a40a8
    resource: repo://order/src/main/java/com/example/order/application/query/order/GetOrderQueryHandler.java
  - id: openwiki-source-a6fc9f9944c984eb9c1e61a6
    resource: repo://order/src/main/java/com/example/order/application/query/order/ListOrdersQueryHandler.java
  - id: openwiki-source-b00d4312e38883b0c3231809
    resource: repo://order/src/main/java/com/example/order/interfaces/rest/OrderCommandController.java
  - id: openwiki-source-85f28f64471f782c274d0aa6
    resource: repo://order/src/main/resources/application.yml
  - id: openwiki-source-4bc77f4a20a6e7618e6e007c
    resource: repo://order/src/test/java/com/example/order/component/OrderComponentTest.java
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# System boundaries and data ownership

The executable example comprises three independently built Spring Boot services, two published Java libraries, and a separate live-system test project. Each service has its own application entrypoint, Gradle build, Flyway migrations, and Helm chart. The repository also distributes architecture workflow skills; those are a documentation and design toolchain, described in [Architecture evidence](../workflows/architecture-evidence.md).

## State and responsibility

| Owner | Public surface | Authoritative state | Supporting mechanisms |
|---|---|---|---|
| Catalog | `/api/v1/books`, including stock reserve/release | Books and stock counts in its PostgreSQL database | Redis detail cache; cancellation event consumer |
| Order | `/api/v1/orders`, including cancellation | Order lifecycle, item snapshots, and totals in PostgreSQL | Catalog HTTP client; outbox; Elasticsearch read model |
| Notification | Order-event consumers; `/api/v1/notifications?customerId=…` | Notification delivery records in PostgreSQL | Customer lookup stub and email log adapter |
| Seedwork | Library contracts and Spring auto-configuration | Technical tables in each consuming service database | Buses, aggregate events, outbox, deduplication, retry, HTTP errors |
| Shared events | Published Avro SDK and Kafka resource tooling | Event schemas and topic definitions | Generated Avro classes and Schema Registry registration |

The service configurations point at separate `catalog`, `order`, and `notification` databases. This expresses logical ownership; it does not require three PostgreSQL server processes. Redis and Elasticsearch hold derived data, not the authoritative inventory or order write state. See [Runtime configuration](../operations/runtime-and-deployment.md).

## Entry mechanisms and dependency direction

The domain aggregates own business state transitions and depend on shared domain primitives. Persistence interfaces describe domain-facing storage; infrastructure adapters implement those interfaces. Application handlers coordinate I/O and domain decisions.

The current services use two entry mechanisms. Order controllers dispatch commands through `CommandBus`; notification queries dispatch through `QueryBus`. Catalog controllers instead inject typed inbound use-case interfaces and invoke `handle` directly. Catalog's `ReserveStockCommandHandler`, for example, implements `ReserveStockUseCase`. Do not infer a bus dispatch merely from a class being named a handler. [Seedwork](../concepts/seedwork.md) explains the bus implementation and event handoff.

## Representative runtime flow

```mermaid
flowchart LR
    Client -->|place order| Order
    Order -->|check, reserve, compensate over HTTP| Catalog
    Catalog --> CatalogDB[(Catalog PostgreSQL)]
    Order --> OrderDB[(Order PostgreSQL)]
    OrderDB -->|outbox CDC| Kafka
    OrderDB -->|orders CDC and sink| ES[(Elasticsearch)]
    Order -->|query| ES
    Kafka -->|cancelled| Catalog
    Kafka -->|order lifecycle events| Notification
    Notification --> NotificationDB[(Notification PostgreSQL)]
    Notification --> EmailLog[Email log adapter]
```

Placement checks stock, calculates a price, reserves each line through Catalog HTTP calls, then saves the placed order. Failures after successful reservations trigger best-effort HTTP releases. This is a multi-service sequence, not one distributed database transaction. Follow [Order lifecycle](../workflows/order-lifecycle.md) and [Catalog inventory](../workflows/catalog-inventory.md) for the precise limits.

Cancellation saves an order-domain event; the order outbox maps it to an Avro integration event. Catalog consumes cancellation to release stock; Notification consumes order lifecycle events to create delivery records. Order read-model CDC is a separate pipeline from integration-event delivery. Its detail query can fall back to PostgreSQL when Elasticsearch yields no result; list queries remain Elasticsearch-backed. See [Event delivery](../integrations/event-delivery.md), [Read model](../integrations/order-read-model.md), and [Notifications](../workflows/notifications.md).

## Evidence and change navigation

Start with the [Order command controller](../../order/src/main/java/com/example/order/interfaces/rest/OrderCommandController.java), [Catalog command controller](../../catalog/src/main/java/com/example/catalog/interfaces/rest/BookCommandController.java), and [Notification query controller](../../notification/src/main/java/com/example/notification/interfaces/rest/NotificationController.java). Then follow handlers into domain models and adapters rather than treating the README's conceptual diagrams as runtime guarantees.

The [Order component test](../../order/src/test/java/com/example/order/component/OrderComponentTest.java) demonstrates HTTP, database, and mocked Catalog boundaries. It does not run the production CDC connectors. The [verification map](../testing/verification-map.md) distinguishes those component assertions from tests against running services.
