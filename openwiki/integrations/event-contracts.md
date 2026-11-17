---
type: integration
title: Avro contracts and Kafka resources
description: Describes the shared event SDK, domain-to-wire mapping, Kafka topic ownership, and the registration tools' actual update behavior.
tags: [avro, kafka, contracts, schema-registry]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-06ff0c162b8c9f923d34e96e
    resource: repo://catalog/src/main/java/com/example/catalog/infrastructure/messaging/outbox/CatalogOutboxMapper.java
  - id: openwiki-source-ce1cf260523a2f491f999098
    resource: repo://catalog/src/main/java/com/example/catalog/interfaces/messaging/consumer/OrderEventConsumer.java
  - id: openwiki-source-e8dd467e1b5b4a421949c0ae
    resource: repo://notification/src/main/java/com/example/notification/interfaces/messaging/consumer/OrderEventConsumer.java
  - id: openwiki-source-034d986e19f10c7bb1af8687
    resource: repo://order/src/main/java/com/example/order/infrastructure/messaging/outbox/OrderOutboxMapper.java
  - id: openwiki-source-85f28f64471f782c274d0aa6
    resource: repo://order/src/main/resources/application.yml
  - id: openwiki-source-de75c3b19ff7b36a0667b620
    resource: repo://shared-events/build.gradle.kts
  - id: openwiki-source-32384e55c9b309db1a6d3b1f
    resource: repo://shared-events/helm/scripts/create-topics.sh
  - id: openwiki-source-bd3ecd4787d957523bc24a92
    resource: repo://shared-events/helm/scripts/manage-acls.sh
  - id: openwiki-source-6de4a2edcb4d0465a7a749dc
    resource: repo://shared-events/helm/scripts/register-schemas.sh
  - id: openwiki-source-8aa6ad8f2e210b7cba49b9a3
    resource: repo://shared-events/src/main/avro/com/example/events/v1/OrderCancelled.avsc
  - id: openwiki-source-1c8219446c1725d50c16184d
    resource: repo://shared-events/src/main/avro/com/example/events/v1/OrderPlaced.avsc
  - id: openwiki-source-4763c35cc91109ad2214913b
    resource: repo://shared-events/src/main/resources/kafka/topics.yaml
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Avro contracts and Kafka resources

`shared-events` publishes `com.example:shared-events:0.1.0`. Its Gradle Avro plugin generates Java types from schemas under `src/main/avro/com/example/events/v1`; generated fields are private and setters are disabled. The library exposes Avro and Confluent serializer dependencies and contains a handwritten `KafkaResourceConstants` class for topic names and service identifiers. It contains no bookstore domain aggregate implementation.

## Domain events versus integration events

Order and Catalog keep their own domain-event types. Their infrastructure `OutboxMapper` implementations translate supported events into generated Avro records and serialize them with `KafkaAvroSerializer` configured from the service producer factory. The stored payload therefore already contains the Confluent wire framing used by downstream deserializers. Both mappers return an empty result for unsupported domain events.

Order maps placed, cancelled, confirmed, and shipped events. Catalog maps stock reserved and released. The outbox writer chooses the aggregate ID as message key: an order ID for order events, a book ID for stock events. Delivery and transaction boundaries are covered in [Event delivery](event-delivery.md).

| Topic | Mapper producer | Listener present in service source |
|---|---|---|
| `bookstore.order.placed` | Order | Notification |
| `bookstore.order.confirmed` | Order | Notification |
| `bookstore.order.cancelled` | Order | Notification and Catalog |
| `bookstore.order.shipped` | Order | Notification |
| `bookstore.stock.reserved` | Catalog | No Order stock-event listener in the inspected source |
| `bookstore.stock.released` | Catalog | No service listener in the inspected source |

`topics.yaml` additionally declares `order-read-model` as a placed-event consumer and `order` as a reserved-event consumer for resource provisioning. Those declarations are not proof that corresponding Java consumers exist. The actual order search pipeline tails the `orders` table; see [Order read model](order-read-model.md). Similarly, the topic description mentioning confirmation after payment does not establish a payment integration: current small-order confirmation is triggered locally after placement commits.

## Payload and evolution seams

`OrderPlaced` carries event/order/customer IDs, the email snapshot, item snapshots, total cents, currency, and occurrence time. Its currency default is `CNY`. `OrderCancelled` carries the cancelled items so Catalog can release their quantities; `items` has an empty-array default. Both embed an `OrderItem` record with book ID, title, quantity, and unit-price cents. The item record has no per-item currency field. Occurrence times use Avro `timestamp-millis`.

Preserve the distinction between contract fields and consumer behavior: Notification's current placement handler does not use the email snapshot to choose its recipient; it dispatches a customer-ID-based command. See [Notifications](../workflows/notifications.md).

## Provisioning and its limits

The topic catalog declares six topics with three partitions and replication factor one. Helm supplies scripts for topic creation, schema registration, and ACL additions, while separate `avro-schemas` and `kafka-topics-config` ConfigMaps supply the schema and topic data.

The scripts have different repeat-run semantics:

- Topic creation uses `--create --if-not-exists`; it does not resize an existing topic to match edited values.
- Schema registration maps topics to `<topic>-value` subjects and skips a subject whenever its latest-version endpoint exists. Editing an `.avsc` and rerunning this script does not publish a new version for an existing subject.
- ACL management adds producer WRITE/DESCRIBE, consumer READ/DESCRIBE, and prefixed consumer-group READ permissions. It maps the `order-read-model` identifier to the `order` principal. Additive ACL calls do not reconcile removed permissions.

Service configurations set `auto.register.schemas=false`, making registered schemas a prerequisite for producer serialization. Generating or publishing the Java SDK alone is insufficient to update Schema Registry. Review both schema registration and consumers when changing the contract; the provisioning source does not establish a deployed compatibility policy.

## Source and verification routes

Read [OrderOutboxMapper](../../order/src/main/java/com/example/order/infrastructure/messaging/outbox/OrderOutboxMapper.java), [CatalogOutboxMapper](../../catalog/src/main/java/com/example/catalog/infrastructure/messaging/outbox/CatalogOutboxMapper.java), [topics.yaml](../../shared-events/src/main/resources/kafka/topics.yaml), and [register-schemas.sh](../../shared-events/helm/scripts/register-schemas.sh). Notification handler tests exercise translation from generated event classes to commands; component tests use test serializers and do not prove production Schema Registry provisioning. See [Verification](../testing/verification-map.md) and [Deployment](../operations/runtime-and-deployment.md).
