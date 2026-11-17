---
type: workflow
title: Event-driven notification records
description: Traces order events into notification commands, recipient lookup and delivery records, with the actual demo adapters and Kafka transaction context.
tags: [notification, kafka, email, transactions]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-6ec343a014ee271cd01c1f40
    resource: repo://notification/src/main/java/com/example/notification/application/command/notification/SendNotificationCommandHandler.java
  - id: openwiki-source-06f13a86890dbd1b55bddb88
    resource: repo://notification/src/main/java/com/example/notification/application/query/notification/ListNotificationsQueryHandler.java
  - id: openwiki-source-3c3030d398f43fd9619a5d07
    resource: repo://notification/src/main/java/com/example/notification/domain/model/Notification.java
  - id: openwiki-source-1f2d08d29957a8b952c8a76f
    resource: repo://notification/src/main/java/com/example/notification/infrastructure/client/customer/StubCustomerClient.java
  - id: openwiki-source-66e7714b7d5f81c9eecfc635
    resource: repo://notification/src/main/java/com/example/notification/infrastructure/client/email/LogEmailAdapter.java
  - id: openwiki-source-07632e26480be875cc4c4b97
    resource: repo://notification/src/main/java/com/example/notification/infrastructure/repository/jpa/NotificationPersistenceAdapter.java
  - id: openwiki-source-d63abd099d49f9e301e57d69
    resource: repo://notification/src/main/java/com/example/notification/interfaces/messaging/consumer/OrderCancelledHandler.java
  - id: openwiki-source-e8dd467e1b5b4a421949c0ae
    resource: repo://notification/src/main/java/com/example/notification/interfaces/messaging/consumer/OrderEventConsumer.java
  - id: openwiki-source-596a353d709115d428402dfc
    resource: repo://notification/src/main/java/com/example/notification/interfaces/messaging/consumer/OrderPlacedHandler.java
  - id: openwiki-source-5c13b4721950d99b88333e05
    resource: repo://notification/src/main/java/com/example/notification/interfaces/rest/NotificationController.java
  - id: openwiki-source-74c57bc7db7612e8ad15fcbf
    resource: repo://notification/src/main/resources/application.yml
  - id: openwiki-source-1e5de108acedbf282d0f163e
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/bus/SpringCommandBus.java
  - id: openwiki-source-7cee329bba191761e82521cb
    resource: repo://seedwork/src/main/java/com/example/seedwork/infrastructure/kafka/KafkaMessageProcessor.java
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Event-driven notification records

Notification consumes the four order lifecycle topics and persists delivery records. Its public REST surface lists records by customer; event handlers initiate sends. The checked-in default delivery is a log simulation, and recipient lookup is a deterministic stub.

## From event to command

`OrderEventConsumer` passes each typed Avro record and acknowledgment to `KafkaMessageProcessor` with its corresponding `RetryableKafkaHandler`. The handlers expose the event class and event UUID for generic dispatch/deduplication, then send `SendNotificationCommand` through the command bus.

For example, `OrderPlacedHandler` builds an EMAIL command with customer ID and localized subject/body including order ID and total. It does not pass the event's `customerEmail` snapshot. The command handler independently calls `CustomerClient.findEmail(customerId)`. Changing the Avro email field alone therefore does not change the destination selected by the current notification path.

Cancellation text is truncated to 500 Unicode code points and HTML-escaped before being inserted into the body. Other lifecycle handlers select their own text. Schema ownership is described in [Event contracts](../integrations/event-contracts.md); event origins are described in [Order lifecycle](order-lifecycle.md).

## Record lifecycle and delivery outcomes

`Notification.create()` assigns a new ID in PENDING state. The command handler performs this sequence:

1. Resolve an email using customer ID.
2. If absent, create a record with no recipient, mark it FAILED with a reason, save once, and return.
3. Otherwise create and save a PENDING record.
4. Call `EmailSender.send()` and mark SENT on normal return, or catch its exception and mark FAILED.
5. Save the final state and return normally.

The aggregate's `markSent` and `markFailed` methods set state and register local domain events without validating a preceding state. Those events are transferred through the JPA event bridge on save. The source contains no notification-specific mapper translating them into a shared Kafka contract; a local domain event is not by itself a published integration event.

The JPA entity stores customer, recipient, channel, payload, delivery status, failure reason, version, and creation/update timestamps. Persistence reloads an existing row by notification ID before updating it.

## Transaction context changes the sequence's durability

The send handler has no transaction annotation, and the persistence adapter's `save()` uses ordinary `@Transactional`. Called without an existing transaction, its two save calls can each establish a transaction around database work.

The actual Kafka entry path is different: `KafkaMessageProcessor.process()` starts a transaction and invokes the handler synchronously through the command bus. Ordinary adapter transactions join that surrounding transaction. Therefore the source comments saying “PENDING commits first” and “send outside any transaction” are not guarantees of the Kafka-driven path. The scheduled retry entry also invokes handlers from a transaction.

A future real email adapter would introduce an external effect that cannot be rolled back with the database. Event-ID deduplication suppresses already-recorded successes; it does not guarantee exactly-once external delivery if sending succeeds and the database transaction subsequently fails. [Event delivery](../integrations/event-delivery.md) explains the acknowledgment and retry boundaries.

Email-send exceptions and missing recipients are converted to persisted FAILED outcomes and normal handler returns, so they do not automatically trigger generic Kafka retry. Customer-lookup or persistence exceptions can still escape into that processor.

## Demo adapters and read API

The default `stub` profile activates `StubCustomerClient`, returning `customer.<UUID>@example.com`. `LogEmailAdapter` is enabled when `notification.email.log-only=true` or absent; it logs masked recipient information, subject, and body. The source does not include the SMTP replacement mentioned in its comment. Turning off log-only delivery or replacing the stub profile requires supplying suitable port implementations, not merely setting a flag.

`GET /api/v1/notifications` requires `customerId`, defaults to page 0 and size 20, and dispatches through `QueryBus`. The query handler loads Notifications through the domain repository and maps them into results containing ID, customer ID, recipient, channel, subject, and status. This read path uses domain objects, unlike Order's direct database detail projection.

## Source and focused tests

Follow [SendNotificationCommandHandler](../../notification/src/main/java/com/example/notification/application/command/notification/SendNotificationCommandHandler.java), [NotificationPersistenceAdapter](../../notification/src/main/java/com/example/notification/infrastructure/repository/jpa/NotificationPersistenceAdapter.java), and [OrderEventConsumer](../../notification/src/main/java/com/example/notification/interfaces/messaging/consumer/OrderEventConsumer.java). [SendNotificationCommandHandlerTest](../../notification/src/test/java/com/example/notification/application/command/notification/SendNotificationCommandHandlerTest.java) checks sent, failed-send, and missing-recipient outcomes with mocked ports; it does not establish database commit timing. The component test covers consumed-event records and duplicate suppression while mocking customer lookup. See [Verification](../testing/verification-map.md).
