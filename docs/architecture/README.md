# Architecture Decision Records

This directory contains ADRs (Architecture Decision Records) for the Online Bookstore demo project.

Each ADR documents a significant architectural decision: its context, the options considered, the decision made, and the consequences (positive and negative).

## Format

ADRs follow the [Michael Nygard template](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions):

- **Status**: Proposed / Accepted / Deprecated / Superseded by ADR-XXX
- **Context**: Why this decision was needed
- **Decision**: What was decided
- **Consequences**: Trade-offs, both positive and negative

## Index

| ADR | Title | Status |
|---|---|---|
| [ADR-001](ADR-001-explicit-architecture-over-layered.md) | Adopt Explicit Architecture over Traditional Layered Architecture | Accepted |
| [ADR-002](ADR-002-cqrs-scope-order-service.md) | Apply CQRS Only to order | Accepted |
| [ADR-003](ADR-003-event-schema-ownership.md) | Centralize Event Schemas in shared-events Module | Accepted |
| [ADR-004](ADR-004-istio-mesh.md) | Use Istio Service Mesh Instead of Application-Level Resilience Libraries | Accepted |
| [ADR-005](ADR-005-outbox-pattern.md) | Use Outbox Pattern for Guaranteed Domain Event Delivery | Accepted |
| [ADR-006](ADR-006-database-per-service.md) | Database-per-Service with No Shared Tables | Accepted |
| [ADR-007](ADR-007-java21-virtual-threads.md) | Use Java 21 with Virtual Threads and Modern Language Features | Accepted |
| [ADR-009](ADR-009-kafka-consumer-idempotency-retry.md) | Kafka Consumer Idempotency and DB-Backed Retry | Accepted |
| [ADR-010](ADR-010-opentelemetry-observability.md) | OpenTelemetry via Kubernetes Operator for Unified Observability | Accepted |
| [ADR-011](ADR-011-swaggerhub-pactflow-bdct.md) | SwaggerHub + PactFlow Bi-Directional Contract Testing | Accepted |

## How to Add a New ADR

1. Copy the template below into a new file named `ADR-{NNN}-{short-title}.md`
2. Fill in all sections
3. Add an entry to the index table above
4. If the new ADR supersedes an existing one, update the old ADR's status to `Superseded by ADR-{NNN}`

```markdown
# ADR-NNN: Title

- **Status**: Proposed
- **Date**: YYYY-MM-DD
- **Deciders**:

---

## Context

## Decision

## Consequences

### Positive

### Negative
```
