# Files

- [Avro contracts and Kafka resources](event-contracts.md) - Describes the shared event SDK, domain-to-wire mapping, Kafka topic ownership, and the registration tools' actual update behavior.
- [Outbox, deduplication, and retry delivery](event-delivery.md) - Traces event persistence, scheduler and Debezium relays, consumer acknowledgments, and database-backed retries, including current delivery limitations.
- [Order CDC and Elasticsearch read model](order-read-model.md) - Follows order JSONB snapshots through Debezium and the Elasticsearch sink, then explains query fallback and the verification limits of HTTP tests.
