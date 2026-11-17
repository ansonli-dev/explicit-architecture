---
type: integration
title: Order CDC and Elasticsearch read model
description: Follows order JSONB snapshots through Debezium and the Elasticsearch sink, then explains query fallback and the verification limits of HTTP tests.
tags: [cqrs, cdc, elasticsearch, postgres, consistency]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-bd8aef37d46351baf382c072
    resource: repo://e2e/src/test/java/com/example/e2e/BaseE2ETest.java
  - id: openwiki-source-1fe32c90bec203a34c38a6e2
    resource: repo://e2e/src/test/java/com/example/e2e/OrderReadModelItemsE2ETest.java
  - id: openwiki-source-08fd646658da82eda43f9ea3
    resource: repo://order/helm/templates/es-sink-connector-job.yaml
  - id: openwiki-source-b77d400d60c463e3b5289b4f
    resource: repo://order/helm/templates/orders-cdc-connector-job.yaml
  - id: openwiki-source-01c22f8628299fa3142a40a8
    resource: repo://order/src/main/java/com/example/order/application/query/order/GetOrderQueryHandler.java
  - id: openwiki-source-a6fc9f9944c984eb9c1e61a6
    resource: repo://order/src/main/java/com/example/order/application/query/order/ListOrdersQueryHandler.java
  - id: openwiki-source-e22bd3d65b4c5a3cb81bc0aa
    resource: repo://order/src/main/java/com/example/order/infrastructure/repository/elasticsearch/converter/JsonStringToListConverter.java
  - id: openwiki-source-fdea5cd059be9e486bea23f5
    resource: repo://order/src/main/java/com/example/order/infrastructure/repository/elasticsearch/OrderElasticDocument.java
  - id: openwiki-source-bc1d01a131000d2a491a07d5
    resource: repo://order/src/main/java/com/example/order/infrastructure/repository/elasticsearch/OrderSearchAdapter.java
  - id: openwiki-source-db6d32e33790bb927fe47426
    resource: repo://order/src/main/java/com/example/order/infrastructure/repository/jpa/OrderJpaEntity.java
  - id: openwiki-source-b17c4746d180fcc9bf741aa2
    resource: repo://order/src/main/resources/db/migration/V0102__orders_items_jsonb.sql
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Order CDC and Elasticsearch read model

PostgreSQL owns the order write state. Elasticsearch serves derived order detail and customer/status search projections. The read model is populated by table CDC and an external sink connector; it is separate from the Avro integration events described in [Event delivery](event-delivery.md).

## Single-row snapshot and connector flow

Migration `V0102` moves line items from `order_item` into `orders.items` JSONB, migrates existing rows, drops the old table, and sets replica identity full. `OrderJpaEntity.ItemJson` stores snake_case keys such as `book_id` and `unit_price_cents`, while preserving item ID, title, currency, and quantity.

```mermaid
flowchart LR
    Save[Order persistence] --> PG[(orders row plus items JSONB)]
    PG --> CDC[order-cdc-connector]
    CDC --> Topic[debezium.public.orders]
    Topic --> Sink[order-es-sink-connector]
    Sink --> ES[(orders index)]
    ES --> Adapter[OrderSearchAdapter]
    Adapter --> Query[Detail or list result]
```

The Orders CDC hook registers a PostgreSQL connector with slot `order_cdc_slot` and publication `dbz_orders_publication`. It snapshots existing rows initially, then streams `public.orders`. `ExtractNewRecordState` removes the Debezium envelope; configured deletion handling drops deletions and tombstones. This pipeline is configured for lifecycle updates such as cancellation, not a documented hard-delete synchronization path.

The sink hook uses Aiven's `OpensearchSinkConnector` pointed at the Elasticsearch endpoint. Its transformations extract `id` from the record key, rename selected snake_case order columns to camelCase, and route records into the `orders` index. It keeps the items JSON string intact. Both hook jobs update an existing connector by PUT or create it by POST. Endpoint and connector names are fixed in the templates, so changing only service Elasticsearch configuration does not automatically change the sink destination.

## Read behavior and consistency

`OrderElasticDocument` represents `items` as unindexed text. `OrderSearchAdapter` parses that string with `ItemDocumentListConverter`, whose item record maps snake_case fields back to Java components. Null, blank, or malformed JSON returns an empty item list; parse failures are logged.

| Read | Successful Elasticsearch result | Miss or adapter exception |
|---|---|---|
| Order detail | Returned immediately | Adapter returns empty; handler reads a PostgreSQL DTO projection and throws not-found only if that is absent too |
| Customer/status list | Returns paged search results as summaries | Adapter logs and returns an empty list; no database fallback |

An existing Elasticsearch hit wins even if it is stale. The detail handler does not compare versions or freshness with PostgreSQL. A document whose item JSON fails to parse can still be returned with an empty list, so that condition does not itself trigger fallback. Search failures and genuinely empty search results also have the same list response shape. These are practical consequences of the current code, not guarantees of read-your-writes consistency.

The PostgreSQL detail adapter builds DTOs directly from JPA entity data. It shares persistence implementation with the write port but does not reconstitute an Order aggregate for that projection. Follow [Order lifecycle](../workflows/order-lifecycle.md) for the write transaction.

## What the tests establish

- [GetOrderQueryHandlerTest](../../order/src/test/java/com/example/order/application/query/order/GetOrderQueryHandlerTest.java) proves Elasticsearch precedence, PostgreSQL fallback on an empty search result, and not-found when both return empty.
- [OrderSearchAdapterTest](../../order/src/test/java/com/example/order/infrastructure/repository/elasticsearch/OrderSearchAdapterTest.java) inserts a CDC-shaped document into containerized Elasticsearch and checks returned item details and filtering. It validates the adapter and fixture shape, not connector execution.
- [OrderComponentTest](../../order/src/test/java/com/example/order/component/OrderComponentTest.java) explicitly runs without the sink connector. Its test named for Elasticsearch document creation asserts a successful detail HTTP response, which can come from PostgreSQL.
- [OrderReadModelItemsE2ETest](../../e2e/src/test/java/com/example/e2e/OrderReadModelItemsE2ETest.java) polls the public detail endpoint for item data. `BaseE2ETest.getOrderFromReadModel()` calls that same fallback-capable route, with no assertion identifying the backend. Therefore these assertions alone cannot prove CDC propagation. Its multiple-items-named case creates two separate single-item orders.

When verifying the deployed pipeline, distinguish connector health and index contents from fallback-capable API behavior. This wiki generation inspected code and assertions; it did not execute connectors against a cluster. See [Runtime configuration](../operations/runtime-and-deployment.md) and the broader [verification map](../testing/verification-map.md).
