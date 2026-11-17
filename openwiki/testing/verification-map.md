---
type: testing
title: Tests and verification boundaries
description: Routes changes to focused domain, handler, adapter, component, and live-system tests, identifying infrastructure requirements and what existing assertions do not prove.
tags: [testing, junit, testcontainers, verification]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-07a99ccad423df862d696119
    resource: repo://catalog/src/test/java/com/example/catalog/component/CatalogComponentTest.java
  - id: openwiki-source-b7e19cc7a72a67e750591677
    resource: repo://catalog/src/test/java/com/example/catalog/infrastructure/cache/RedisBookCacheTest.java
  - id: openwiki-source-6b6f5a9867633a4d4bb30a89
    resource: repo://catalog/src/test/resources/application-test.yml
  - id: openwiki-source-bb24b4f1c7a1ba7a83820158
    resource: repo://e2e/build.gradle.kts
  - id: openwiki-source-bd8aef37d46351baf382c072
    resource: repo://e2e/src/test/java/com/example/e2e/BaseE2ETest.java
  - id: openwiki-source-408a0d19e852d4a446f6d3ec
    resource: repo://e2e/src/test/java/com/example/e2e/OrderCancellationE2ETest.java
  - id: openwiki-source-6541d1997100c3b3f9de953e
    resource: repo://e2e/src/test/java/com/example/e2e/OrderPlacementE2ETest.java
  - id: openwiki-source-95f7e63f47603e57747fd889
    resource: repo://notification/src/test/java/com/example/notification/component/NotificationComponentTest.java
  - id: openwiki-source-32207f2c0de7e4496bde17a1
    resource: repo://notification/src/test/java/com/example/notification/integration/NotificationIntegrationTest.java
  - id: openwiki-source-3b96fec59735cc14d4d3c16d
    resource: repo://notification/src/test/resources/application-test.yml
  - id: openwiki-source-6a49a9de8028ff37182d7059
    resource: repo://order/src/main/java/com/example/order/application/command/order/CancelOrderCommandHandler.java
  - id: openwiki-source-b90939a17da7dbd72909971c
    resource: repo://order/src/test/resources/application-test.yml
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Tests and verification boundaries

Tests live in independent Gradle projects. Start with the narrowest test that reaches the changed behavior, then widen only when a boundary matters. This wiki records inspected test code; initialization did not execute the Java suites or establish deployment health.

## Pick the boundary

| Concern | Representative test | What it exercises |
|---|---|---|
| Order transitions | `order/.../domain/model/OrderTest.java`, `OrderAutoConfirmTest.java` | Domain state and emitted events without infrastructure |
| Price policy | `order/.../domain/service/OrderPricingServiceTest.java` | Pure pricing calculation |
| Stock arithmetic | `catalog/.../domain/model/StockLevelTest.java` | Inventory value-object invariants |
| Placement orchestration | `PlaceOrderCommandHandlerTest` | Mock Catalog/persistence with real pricing service |
| Notification outcomes | `SendNotificationCommandHandlerTest` | Recipient lookup and success/failure with mocked ports |
| PostgreSQL mapping | `BookPersistenceAdapterTest`, `OrderPersistenceAdapterTest` | JPA slices and real database schema/mapping |
| Redis mapping | `RedisBookCacheTest` | Cache miss, round trip, and explicit invalidation |
| Elasticsearch mapping | `OrderSearchAdapterTest` | CDC-shaped document reads and search filtering |
| Service assembly | Three service component tests | HTTP/Kafka/application/database wiring within a service boundary |
| Running system | `e2e` project | Public HTTP behavior against configured live services |

## Focused commands

Run these from the repository root after publishing the shared libraries as described in [Quickstart](../quickstart.md):

```bash
./order/gradlew -p order test --tests 'com.example.order.domain.model.OrderTest'
./order/gradlew -p order test --tests 'com.example.order.application.command.order.PlaceOrderCommandHandlerTest'
./catalog/gradlew -p catalog test --tests 'com.example.catalog.domain.model.StockLevelTest'
./notification/gradlew -p notification test --tests 'com.example.notification.application.command.notification.SendNotificationCommandHandlerTest'
```

These selected test classes do not start the real service stores, although compilation and dependency resolution still require the configured Java toolchain and dependencies. For a storage change, select the corresponding adapter test instead; those need a working container environment. The builds configure JUnit Platform and preview-enabled test JVMs. Order also accepts `-PexcludeTags=...`; a comment about separate contract CI is not evidence of executable Pact tests in the inspected tree.

## Fixtures and substitutions

Service test profiles use `jdbc:tc:postgresql:16` URLs, so a JPA slice can still start PostgreSQL through Testcontainers even without an explicit `@Container` field. Redis tests start `redis:7-alpine`; the Order search adapter and component tests use Elasticsearch 8.13.4 containers.

Catalog's component test starts a random-port server and embedded Kafka, mocks `BookCache`, and resets database tables with `cleanup.sql`. It checks REST/database effects and consumes stock events using Confluent serialization against `mock://test`. It does not prove real Redis behavior or production Schema Registry registration.

Order's component test adds containerized Elasticsearch and a `MockWebServer` Catalog substitute. The test profile switches outbox delivery from production Debezium to a 500-ms scheduler. The sink connector is absent, and the detail endpoint can succeed through PostgreSQL fallback.

Notification's component test uses embedded Kafka and PostgreSQL, mocks the customer client and outbox mapper, and accepts either SENT or FAILED delivery status for the basic consumed-event case. Its default mocked customer lookup returns empty. The duplicate-event case checks a single persisted record, but this fixture does not prove real SMTP delivery or every retry/crash race. `NotificationIntegrationTest.java` contains only a replacement comment and package declaration; the active component test owns that coverage.

## Live-system tests

Run the E2E project only against an intended test environment: its HTTP helpers create books and orders and do not clean them up. Gradle resolves base URLs from project properties first, then `CATALOG_BASE_URL`, `ORDER_BASE_URL`, and `NOTIFICATION_BASE_URL`, then localhost ports 8081–8083.

```bash
./e2e/gradlew -p e2e test --tests 'com.example.e2e.HealthCheckE2ETest'
./e2e/gradlew -p e2e test --tests 'com.example.e2e.OrderPlacementE2ETest'
```

Interpret assertions rather than test names:

- Placement tests verify stock reduction and eventual HTTP order visibility. The insufficient-stock case asserts status `>=400`, not specifically a 4xx status.
- Read-model item tests call a fallback-capable detail endpoint; they do not independently identify Elasticsearch as the response source. See [Order read model](../integrations/order-read-model.md).
- Cancellation tests assert restored stock immediately after the cancel response. Current `CancelOrderCommandHandler` only changes and saves the order; Catalog releases stock from Kafka later. These immediate assertions are stronger than the implemented asynchronous contract and may race. See [Order lifecycle](../workflows/order-lifecycle.md).

## Evidence gaps are scoped

The focused placement handler tests cover successful placement and insufficient stock; they do not establish compensation behavior under every partial reservation, timeout, or persistence failure. Seedwork's retry source warrants separate transaction and failure-path verification before claiming reliable dead-lettering; see [Event delivery](../integrations/event-delivery.md). Such gaps are limits on evidence, not instructions to add new functionality during documentation work.

For non-runtime architecture packages, the repository has a Python structural validator and self-test. Its role is described in [Architecture evidence](../workflows/architecture-evidence.md); passing it cannot prove a specification's semantic correctness.
