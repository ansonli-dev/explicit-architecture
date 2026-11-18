# Explicit Architecture · Online Bookstore

[简体中文](README-zh.md)

A Java reference project that uses an online bookstore to demonstrate domain boundaries, ports and adapters, and CQRS. The [React storefront](https://github.com/ansonli-dev/react-reference-architecture) uses HTTP v1 for catalog and checkout. Keycloak signs readers in, and asynchronous checkout keeps inventory reservation and order placement consistent.

- [Catalog](catalog/README.md) owns the public book catalog, prices, and inventory.
- [Order](order/README.md) owns quotes, submissions, orders, cancellations, and the search projection.
- [Notification](notification/README.md) handles order events. [Shared events](shared-events/README.md) defines the Avro contracts.

## Quick Start

Use JDK 21 and Docker Compose. The [bookstore runbook](demo/README.md) covers building the Java services, starting the storefront, seeding inventory, and running the browser tests.

The Java HTTP contracts are [Catalog v1](catalog/contracts/catalog-v1.yaml) and [Order v1](order/contracts/order-v1.yaml).

## Project documentation

[Architecture specification](docs/architecture/architecture-spec.md) · [Clarified Architecture](docs/architecture/clarified-architecture/clarified-architecture-en.md) · [Architecture decisions](docs/architecture/adr/README.md) · [Testing strategy](docs/testing-strategy/README.md)
