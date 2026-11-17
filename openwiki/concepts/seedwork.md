---
type: concept
title: Shared runtime primitives and extension seams
description: Explains seedwork bus dispatch, aggregate event publication, auto-configuration, and shared HTTP failure handling as consumed by the bookstore services.
tags: [seedwork, events, spring, extension]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-aaea83473f7ac580b8d07bd0
    resource: repo://order/src/main/java/com/example/order/infrastructure/repository/jpa/OrderPersistenceAdapter.java
  - id: openwiki-source-e3b89160a9e99230d1ea7186
    resource: repo://seedwork/src/main/java/com/example/seedwork/domain/AggregateRoot.java
  - id: openwiki-source-1e5de108acedbf282d0f163e
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/bus/SpringCommandBus.java
  - id: openwiki-source-8217f9c970a8f2b88f84caed
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/bus/SpringQueryBus.java
  - id: openwiki-source-5507edc4317c20bf94149283
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/jpa/AbstractAggregateRootEntity.java
  - id: openwiki-source-1b9df7784dc8b827eae01b4e
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/KafkaIdempotencyAutoConfiguration.java
  - id: openwiki-source-b5c9629f1e505954b08b35b5
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/retry/ConsumerRetryAutoConfiguration.java
  - id: openwiki-source-5ee7ef9f8a3c62e4242cf0cb
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/outbox/OutboxAutoConfiguration.java
  - id: openwiki-source-580ab50df83b8444fa73ceb4
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/web/GlobalExceptionHandler.java
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Shared runtime primitives and extension seams

`com.example:seedwork:0.1.0` is a Java 21 library. It supplies domain and application contracts plus Spring infrastructure; it is not a separately running service. Its build declares several infrastructure dependencies as `compileOnly`, so consumers must supply the JPA, Kafka, web, and metrics dependencies required by the configurations they load.

## Bus dispatch

`BusAutoConfiguration` creates command and query buses from Spring handler lists and a `MeterRegistry`. Each bus resolves the handler's generic input type at construction and stores it in a class-keyed map. Dispatch uses the input object's exact runtime class, calls the handler synchronously, logs completion/failure, and records `seedwork.command.duration` or `seedwork.query.duration` with input-name and outcome tags.

A missing registration throws `IllegalArgumentException` before timing starts. An unresolved generic input is omitted; duplicate registrations use ordinary map replacement rather than an explicit ambiguity check. The bus itself does not establish a database transaction. Register a Spring bean implementing the appropriate generic handler interface when extending a bus-based service. Catalog's direct inbound use cases are a separate entry mechanism; see [System boundaries](../architecture/system-map.md).

## Aggregate-to-Spring event handoff

`AggregateRoot` requires a non-null typed ID and defines equality by concrete aggregate class and ID. It accumulates domain events in memory. `peekDomainEvents()` returns an immutable snapshot; `clearDomainEvents()` clears the aggregate list. The destructive `pullDomainEvents()` API remains available but is deprecated.

The Order persistence adapter illustrates the current handoff:

1. Translate the domain aggregate into a JPA entity inside `save()`.
2. Attach the aggregate's event snapshot to `AbstractAggregateRootEntity`.
3. Call the Spring Data repository's `save()`.
4. Clear the aggregate's event list after that call returns.

The entity bridge exposes its attached events via `@DomainEvents` and clears its own list via `@AfterDomainEventPublication`. Spring transaction listeners can then react in different phases: outbox writing uses `BEFORE_COMMIT`; order auto-confirmation uses `AFTER_COMMIT`. The aggregate list is protected against a repository-save exception before the clear, but clearing after `save()` is not proof that the enclosing transaction has already committed. See [Event delivery](../integrations/event-delivery.md) for durability and relay semantics.

## Auto-configuration and host requirements

The Boot imports resource lists bus, outbox, Kafka idempotency, consumer retry, and web auto-configurations. These have distinct activation rules:

| Mechanism | Actual activation or dependency |
|---|---|
| Outbox event writer | Requires an `OutboxMapper` bean |
| Outbox scheduler | `outbox.relay.strategy=scheduler`, including when missing |
| Persistence discovery | Outbox configuration scans entities and repositories under `com.example` |
| Kafka processor | Kafka listener class present; requires retry persistence adapter |
| Retry scheduler and adapter | `consumer.retry.enabled`, enabled when missing |
| Web advice | Servlet web application with `DispatcherServlet` present |

Disabling retry removes the configuration that normally supplies the processor's retry adapter; it is not sufficient evidence that all Kafka processing remains usable unchanged. Likewise, the outbox publisher and metrics beans are not gated by the presence of an `OutboxMapper`. Treat this library as infrastructure with host prerequisites, not a set of completely independent optional features.

Services include both `classpath:db/seedwork` and `classpath:db/migration` in Flyway. The shared migration files create outbox, processed-event, and retry tables; service migrations begin at `V0100`.

## HTTP failures

The shared lowest-precedence advice maps `NotFoundException` to 404, other `DomainException` instances to 422, and optimistic-lock exceptions to 409. Binding/validation errors have 400 handlers, and route/media failures have dedicated status mappings. The generic fallback returns 500 with a fixed client message while logging the exception.

Error responses contain `status`, `message`, `path`, and `timestamp`; validation responses add `fieldErrors`. There is no blanket handler translating every `IllegalArgumentException` into 400. Whether an invalid domain value becomes 422 or the generic 500 therefore depends on its actual exception type.

## Source and verification routes

Read [SpringCommandBus](../../seedwork/src/main/java/com/example/seedwork/infrastructure/bus/SpringCommandBus.java), [AggregateRoot](../../seedwork/src/main/java/com/example/seedwork/domain/AggregateRoot.java), [the JPA event bridge](../../seedwork/src/main/java/com/example/seedwork/infrastructure/jpa/AbstractAggregateRootEntity.java), and [GlobalExceptionHandler](../../seedwork/src/main/java/com/example/seedwork/infrastructure/web/GlobalExceptionHandler.java). Consumer examples include [OrderPersistenceAdapter](../../order/src/main/java/com/example/order/infrastructure/repository/jpa/OrderPersistenceAdapter.java) and its adapter tests. Consult the [verification map](../testing/verification-map.md) for which service tests exercise the surrounding wiring.
