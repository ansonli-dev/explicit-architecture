# ADR-006: Database-per-Service with No Shared Tables

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## Context

In a microservices architecture, database ownership is a fundamental design question. The alternatives are:

| Pattern | Description |
|---|---|
| Shared database | All services share one PostgreSQL instance and schema |
| Database per service | Each service has its own schema or instance |
| Shared schema, logical separation | One instance, separate schemas per service |

The Online Bookstore has three services with clearly different data needs:
- `catalog`: product catalog, author data, inventory levels
- `order`: order lifecycle, order items, payment status
- `notification`: notification log, delivery status, customer preferences

---

## Decision

Each microservice owns its **own PostgreSQL database** (logically separate; physically may share an instance in development, but must be separate instances in production).

### Rules

1. **No cross SQL queries**: `order` must not `JOIN` against `catalog` tables.
2. **No shared tables**: No table is written to by more than one service.
3. **Data duplication is acceptable**: `order` stores a snapshot of book title and price at time of order. It does not look up `catalog` at query time.
4. **Cross data needs are satisfied by**: (a) synchronous REST calls at write time, or (b) event-driven projection of data from domain events.
5. **Schema migrations are per**: Flyway in each service manages only its own database schema. No shared migration tool.

### Database Assignment

| Service | Database Name | Schema Owner |
|---|---|---|
| `catalog` | `catalog` | catalog only |
| `order` | `order` | order only |
| `notification` | `notification` | notification only |

### Data Snapshot Pattern

When an order is placed, `order` calls `catalog` via REST to verify stock and retrieve the current book price. It then stores a **snapshot** of this data in `order`:

```sql
-- order: order_items table
CREATE TABLE order_items (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL REFERENCES orders(id),
    book_id      UUID NOT NULL,        -- reference only, no FK to catalog
    book_title   VARCHAR(500) NOT NULL, -- snapshot at order time
    unit_price   BIGINT NOT NULL,       -- snapshot at order time (cents)
    quantity     INT NOT NULL
);
```

This means order history remains stable even if the book's price or title changes later in the catalog.

---

## Consequences

### Positive
- **Independent deployability**: Each service can be deployed, scaled, and migrated independently.
- **Fault isolation**: A PostgreSQL outage in `catalog` does not affect `order` reads of existing orders.
- **Technology freedom**: Each service could theoretically use a different database (e.g., `notification` could migrate to MongoDB without affecting others).
- **Clear ownership**: A table belongs to exactly one service. There is no ambiguity about who is responsible for a schema change.

### Negative
- **Data duplication**: Book title and price are stored in both `catalog` and `order`. This is intentional and acceptable.
- **No cross transactions**: Placing an order involves both stock reservation (catalog) and order creation (order). This must be handled via the Saga pattern or compensating transactions, not a database transaction.
- **Eventual consistency**: The catalog's stock level and the order service's view of stock may diverge briefly.

### Saga for Order Placement

The order placement flow spans two services:

```
1. order: create Order in PENDING state (order)
2. order: call catalog to reserve stock (REST)
   a. Success → order: transition Order to CONFIRMED; publish OrderPlaced
   b. Failure → order: transition Order to CANCELLED; publish OrderCancelled
3. catalog: consume StockReserved event → decrement stock in catalog
```

This is a **Choreography-based Saga** — no central coordinator. Each service reacts to events and compensates on failure.

### Local Development

A single PostgreSQL instance (deployed via the infrastructure Helm chart) runs three logical databases initialized by `infrastructure/db/init.sh`:

```sql
CREATE DATABASE catalog;
CREATE DATABASE "order";
CREATE DATABASE notification;
```

In production (Kubernetes), each service uses a separate PostgreSQL instance managed by a PostgreSQL Operator (e.g., CloudNativePG).
