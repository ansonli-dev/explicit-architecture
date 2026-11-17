---
type: quickstart
title: Quickstart and task routing
description: Starts repository exploration with independent Java builds, focused tests, runtime prerequisites, and a task map across the bookstore and architecture workflow suite.
tags: [quickstart, navigation, development, bookstore]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-2c85637bbc3fcf21a45d838d
    resource: repo://catalog/build.gradle.kts
  - id: openwiki-source-342594298fb62993a735eefa
    resource: repo://catalog/src/main/resources/application.yml
  - id: openwiki-source-bb24b4f1c7a1ba7a83820158
    resource: repo://e2e/build.gradle.kts
  - id: openwiki-source-c31500a34f6ac6c5b5106065
    resource: repo://e2e/src/test/java/com/example/e2e/HealthCheckE2ETest.java
  - id: openwiki-source-74c57bc7db7612e8ad15fcbf
    resource: repo://notification/src/main/resources/application.yml
  - id: openwiki-source-895325b430ff49b88beb7112
    resource: repo://order/build.gradle.kts
  - id: openwiki-source-b77d400d60c463e3b5289b4f
    resource: repo://order/helm/templates/orders-cdc-connector-job.yaml
  - id: openwiki-source-2eb2fbcfde7c4960520b7050
    resource: repo://order/settings.gradle.kts
  - id: openwiki-source-85f28f64471f782c274d0aa6
    resource: repo://order/src/main/resources/application.yml
  - id: openwiki-source-b542d3a2ae199a304029615b
    resource: repo://seedwork/build.gradle.kts
  - id: openwiki-source-5ee7ef9f8a3c62e4242cf0cb
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/outbox/OutboxAutoConfiguration.java
  - id: openwiki-source-de75c3b19ff7b36a0667b620
    resource: repo://shared-events/build.gradle.kts
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Quickstart and task routing

This repository contains an online-bookstore example and reusable architecture workflow skills. The runtime example has three Spring Boot services—Catalog, Order, and Notification—plus shared Java libraries and an independent E2E test project. Start with the [system map](architecture/system-map.md) for ownership and actual service connections.

## Build the shared inputs first

Use a JDK 21 toolchain. Projects carry their own Gradle wrappers and settings; service builds enable Java preview features and depend on versioned published libraries rather than a root multi-project dependency graph.

From the repository root:

```bash
./seedwork/gradlew -p seedwork publishToMavenLocal
./shared-events/gradlew -p shared-events publishToMavenLocal
```

Repeat publication after changing either library. Services consume `com.example:seedwork:0.1.0` and `com.example:shared-events:0.1.0` through `mavenLocal()` during local development. Dependency repositories also include external Maven sources, so a first build needs access to those artifacts and the wrapper distribution. The service builds already configure preview JVM flags for Gradle-run applications and tests.

For a small initial verification that does not start service infrastructure:

```bash
./order/gradlew -p order test --tests 'com.example.order.domain.model.OrderTest'
./catalog/gradlew -p catalog test --tests 'com.example.catalog.domain.model.StockLevelTest'
```

These commands still compile the projects and resolve dependencies. Consult [Tests and verification boundaries](testing/verification-map.md) before running full suites: adapter and component tests use containers, while E2E tests require already-running services.

## Run services with their dependencies

Prepare the relevant databases and endpoints before starting applications:

| Service | Default HTTP | Runtime inputs |
|---|---|---|
| Catalog | localhost:8081 | PostgreSQL `catalog`, Redis, Kafka, Schema Registry with stock-event schemas |
| Order | localhost:8082 | PostgreSQL `order`, Catalog HTTP, Elasticsearch, Kafka, Schema Registry with order-event schemas |
| Notification | localhost:8083 | PostgreSQL `notification`, Kafka, Schema Registry; default customer stub and email log adapter |

Application defaults use PostgreSQL on 5432, Kafka on 9092, Schema Registry on 8085, Redis on 6379, and Elasticsearch on 9200. Configure database credentials and URLs for the intended local environment. Flyway runs shared and service migrations, then JPA validates the schema.

Run each service in a separate terminal from the repository root:

```bash
./catalog/gradlew -p catalog bootRun
./order/gradlew -p order bootRun
./notification/gradlew -p notification bootRun
```

Order selects Debezium for outbox delivery by default. If running without that connector, the source-supported scheduler alternative is:

```bash
./order/gradlew -p order bootRun --args='--outbox.relay.strategy=scheduler'
```

That changes integration-event relay only. It does not populate the Elasticsearch read model, whose independent Orders CDC and sink pipeline is described in [Order read model](integrations/order-read-model.md). Detail requests can fall back to PostgreSQL; list requests cannot. Producers disable automatic schema registration by default, so the SDK build alone does not prepare Schema Registry. See [Event contracts](integrations/event-contracts.md).

Use `CATALOG_URL` to redirect Order's Catalog client. Other overrides, Helm inputs, connector prerequisites, and observability boundaries are in [Runtime configuration](operations/runtime-and-deployment.md).

For a running stack, a read-only service-health smoke test is:

```bash
./e2e/gradlew -p e2e test --tests 'com.example.e2e.HealthCheckE2ETest'
```

E2E base URLs can be supplied through `CATALOG_BASE_URL`, `ORDER_BASE_URL`, and `NOTIFICATION_BASE_URL`, with Gradle project properties taking precedence. Other E2E flows create persistent books and orders, so use the intended test environment.

## Find the right evidence

| Task | Start here |
|---|---|
| Understand service boundaries or direct-use-case versus bus entrypoints | [System map](architecture/system-map.md) |
| Extend dispatch, event handoff, or shared HTTP errors | [Seedwork](concepts/seedwork.md) |
| Change price rules, placement, compensation, or cancellation | [Order lifecycle](workflows/order-lifecycle.md) |
| Change stock arithmetic, catalog persistence, or caching | [Catalog inventory](workflows/catalog-inventory.md) |
| Trace missing or failed notification records | [Notifications](workflows/notifications.md) |
| Change Avro payloads, topics, or registration tooling | [Event contracts](integrations/event-contracts.md) |
| Investigate duplicates, acknowledged failures, or stuck retries | [Event delivery](integrations/event-delivery.md) |
| Investigate missing/stale order details or list results | [Order read model](integrations/order-read-model.md) |
| Configure or deploy service processes and connectors | [Runtime configuration](operations/runtime-and-deployment.md) |
| Choose a focused test and understand its limits | [Verification map](testing/verification-map.md) |
| Author/review feature specs or service/capability baselines | [Architecture evidence workflow](workflows/architecture-evidence.md) |

The pages link to implementation and focused tests. Treat source and assertions as authoritative when comments or older diagrams differ. This initialization validated wiki evidence and ran the architecture package validator's self-test; it did not build or run the Java services or their test suites.
