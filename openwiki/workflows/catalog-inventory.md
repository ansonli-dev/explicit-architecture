---
type: workflow
title: Catalog queries and stock mutation
description: Explains Catalog's direct use-case entrypoints, stock-count model, persistence and cache lifecycle, and cancellation-driven release semantics.
tags: [catalog, inventory, cache, persistence]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-7e48f4983eee5d722fcc0641
    resource: repo://catalog/src/main/java/com/example/catalog/application/command/book/ReserveStockCommandHandler.java
  - id: openwiki-source-1454f9ca5e1ea9d218f6a23e
    resource: repo://catalog/src/main/java/com/example/catalog/application/query/book/GetBookQueryHandler.java
  - id: openwiki-source-b329a0e224c7f864342b3752
    resource: repo://catalog/src/main/java/com/example/catalog/application/query/book/GetStockQueryHandler.java
  - id: openwiki-source-8d8a494d8081f315ea2dcd51
    resource: repo://catalog/src/main/java/com/example/catalog/domain/model/Book.java
  - id: openwiki-source-eed28660e8ae654a03411544
    resource: repo://catalog/src/main/java/com/example/catalog/domain/model/StockLevel.java
  - id: openwiki-source-2540f7cfe9198849a3f433ae
    resource: repo://catalog/src/main/java/com/example/catalog/infrastructure/cache/BookCacheInvalidationListener.java
  - id: openwiki-source-8ee19e536b52d484bbf13231
    resource: repo://catalog/src/main/java/com/example/catalog/infrastructure/cache/RedisBookCache.java
  - id: openwiki-source-b340387cdab8306135b6a5dd
    resource: repo://catalog/src/main/java/com/example/catalog/infrastructure/repository/jpa/BookEntityMapper.java
  - id: openwiki-source-86769e36ac299628b4c38402
    resource: repo://catalog/src/main/java/com/example/catalog/infrastructure/repository/jpa/BookJpaEntity.java
  - id: openwiki-source-2f15e18054800705a5dd87d0
    resource: repo://catalog/src/main/java/com/example/catalog/infrastructure/repository/jpa/BookPersistenceAdapter.java
  - id: openwiki-source-fa505c344b6bf061fa35a594
    resource: repo://catalog/src/main/java/com/example/catalog/interfaces/messaging/consumer/OrderCancelledHandler.java
  - id: openwiki-source-e938156c2974c3e6bf65c6a5
    resource: repo://catalog/src/main/java/com/example/catalog/interfaces/rest/BookCommandController.java
  - id: openwiki-source-da49c7b84be55a5381687d6d
    resource: repo://catalog/src/main/java/com/example/catalog/interfaces/rest/BookQueryController.java
  - id: openwiki-source-0db31956b62188c2642a0e7c
    resource: repo://catalog/src/test/java/com/example/catalog/domain/model/StockLevelTest.java
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Catalog queries and stock mutation

Catalog owns book metadata, prices, categories, and total/reserved stock counts. Its REST controllers inject typed inbound use cases directly; they do not dispatch through seedwork buses. The same release use case is called from the cancellation-event handler, connecting HTTP compensation and asynchronous cancellation to the same domain operation.

## Public workflow

| Route under `/api/v1/books` | Effect |
|---|---|
| POST | Build and save a Book; return 201 with details |
| PUT `/{id}` | Apply supplied metadata/price changes and positive restock quantity |
| GET | Page/filter books by category and map domain results to summaries |
| GET `/{id}` | Return a cached detail or load and cache a database result |
| GET `/{id}/stock` | Read the current Book from persistence and report available stock |
| POST `/{id}/stock/reserve` | Reserve quantity for the supplied order ID; return available stock |
| POST `/{id}/stock/release` | Release quantity for the supplied order ID; return 204 |

Order placement uses the dedicated stock route rather than cached book details; see [Order lifecycle](order-lifecycle.md).

## Inventory invariant and identity limits

`StockLevel` is an immutable pair `(total, reserved)`. Construction requires `0 <= reserved <= total`, and available stock is `total - reserved`. Reserve requires a positive quantity and sufficient availability, then increases reserved. Release requires a positive quantity no greater than reserved, then decreases reserved. Restock increases total while leaving reserved unchanged.

`Book.reserve(orderId, quantity)` and `Book.release(orderId, quantity)` replace the stock value and register stock domain events. The order ID is carried in the event; it is not used to look up a persisted per-order reservation. The Book and JPA model hold aggregate counts, not a reservation ledger. Consequently, these operations do not deduplicate repeated HTTP requests by order ID. Releasing twice can either fail because too little remains reserved or subtract stock reserved for another order. A comment claiming release is a no-op for a missing reservation does not match this model.

## Save and concurrency boundaries

Command handlers load a Book, apply a domain operation, and save it. `BookPersistenceAdapter.save()` owns a transaction, resolves or creates the category, maps into a new or existing JPA entity, attaches events through `BookEntityMapper`, and saves. It clears the aggregate events after the repository call and publishes a technical `BookPersistedEvent` for cache eviction.

The JPA entity has a version column. However, the REST command handler's read and save calls are separate transaction boundaries, and the domain Book carries no version token back into `save()`, which reloads the entity. The annotation alone does not demonstrate that the complete read-modify-save sequence prevents every stale update. Adapter tests establish normal round trips and stock updates, not all concurrent reservation interleavings.

Category creation catches an insert constraint exception and attempts a name-based re-fetch in a new transaction. That recovery branch is not evidence that the original save transaction can commit after every database constraint failure.

## Cache lifecycle

Book detail queries use cache-aside: look up `book:<UUID>`, load and map persistence on a miss, then cache for five minutes. Stock queries and book lists bypass this detail cache. The Redis adapter does not swallow get/put failures, so a cache access exception can fail a detail request rather than transparently returning database data.

After a successful database commit, `BookCacheInvalidationListener` evicts the key. It catches invalidation failures and logs that stale data may be served. This protects a committed write from an eviction exception; it does not make Redis and PostgreSQL one atomic store or eliminate cache-fill races.

## Cancellation consumption

Catalog's Kafka consumer routes `OrderCancelled` through `KafkaMessageProcessor`. Its handler iterates item snapshots, skips nonpositive quantities, and invokes `ReleaseStockUseCase` for each valid item. It catches each item's exception, logs it, and continues. Those swallowed failures do not request a generic event retry; absent a later transaction failure, the processor can regard the event as successfully handled. Event-ID deduplication is a separate technical mechanism from per-order stock-release idempotency. Read [Event delivery](../integrations/event-delivery.md) before relying on either guarantee.

## Source and tests

Start with [StockLevel](../../catalog/src/main/java/com/example/catalog/domain/model/StockLevel.java), [BookPersistenceAdapter](../../catalog/src/main/java/com/example/catalog/infrastructure/repository/jpa/BookPersistenceAdapter.java), [RedisBookCache](../../catalog/src/main/java/com/example/catalog/infrastructure/cache/RedisBookCache.java), and [OrderCancelledHandler](../../catalog/src/main/java/com/example/catalog/interfaces/messaging/consumer/OrderCancelledHandler.java). [StockLevelTest](../../catalog/src/test/java/com/example/catalog/domain/model/StockLevelTest.java) asserts arithmetic and invalid releases. JPA, Redis, handler, controller, and component tests cover different boundaries; use the [verification map](../testing/verification-map.md) to select the relevant one.
