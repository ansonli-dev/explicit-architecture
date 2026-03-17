# ADR-002: Apply CQRS Only to order

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## Context

CQRS (Command Query Responsibility Segregation) separates the write model from the read model. It introduces significant complexity: two storage systems, event-driven read-model projections, eventual consistency on the query side, and additional infrastructure to operate.

We need to decide:
1. Which services benefit enough from CQRS to justify the overhead?
2. What form of CQRS to use (same DB with separate models, or separate storage)?

### Service Profiles

| Service | Write Patterns | Read Patterns | Verdict |
|---|---|---|---|
| `catalog` | Low-frequency admin ops (add/update books) | High-frequency reads (browse, search) | Redis cache suffices; CQRS overhead not justified |
| `order` | Transactional order lifecycle; strict consistency on write | Complex queries: by customer, date range, status, full-text; history view | **CQRS justified** |
| `notification` | Insert-only (append log) | Simple read by customer ID | Too simple for CQRS |

---

## Decision

**CQRS is applied exclusively to `order`**, using physically separated storage:

- **Write side**: PostgreSQL — transactional, strongly consistent, normalized schema. Commands (`PlaceOrderCommand`, `CancelOrderCommand`) are handled by dedicated `CommandHandler` classes (`PlaceOrderCommandHandler`, `CancelOrderCommandHandler`), which mutate the `Order` aggregate and persist via `OrderPersistence` (JPA).
- **Read side**: ElasticSearch — denormalized, optimized for search. Queries (`FindOrdersByCustomerQuery`) are handled by dedicated `QueryHandler` classes (`GetOrderQueryHandler`, `ListOrdersQueryHandler`) that go directly to `OrderSearchRepository` (ElasticSearch adapter), bypassing the domain layer entirely.

### Projection Mechanism

When a `CommandHandler` persists a command, it also publishes a domain event (e.g., `OrderPlaced`, `OrderCancelled`) to Kafka via the Outbox Pattern (see ADR-005). A Kafka consumer within `order` (the **projector**) consumes these events and updates the ElasticSearch read model.

```
[Command] → PlaceOrderCommandHandler → PostgreSQL (write)
                                     → Outbox → Kafka
                                                  ↓
                                          OrderPlacedConsumer (projector)
                                                  ↓
                                          ElasticSearch (read)

[Query]   → GetOrderQueryHandler / ListOrdersQueryHandler → ElasticSearch
```

### What CQRS does NOT mean here

- The write side **does not** use Event Sourcing. PostgreSQL stores the current aggregate state, not an event log.
- The command bus is **not** a separate process — it is a method call within the same JVM.
- `catalog` and `notification` use a **simple CRUD approach** with a single data store.

---

## Consequences

### Positive
- `order` read queries (customer history, full-text search, faceted filtering) are served by ElasticSearch without putting load on the transactional PostgreSQL.
- The write path remains strongly consistent and ACID-compliant.
- The separation is visible in the package structure (`application/command/` and `application/query/` are distinct subtrees; `infrastructure/search/` holds the ES adapter).

### Negative
- **Eventual consistency on reads**: After an order is placed, there is a short lag (milliseconds to seconds) before it appears in ElasticSearch queries.
- **Additional infrastructure**: ElasticSearch must be operated alongside PostgreSQL.
- **Projection maintenance**: If the ElasticSearch index schema changes, a re-indexing job must replay all events or re-read PostgreSQL.

### Out of Scope
- Full Event Sourcing (storing events as the primary source of truth) is **not** adopted in this project. This decision can be revisited if audit trail or temporal query requirements arise.
