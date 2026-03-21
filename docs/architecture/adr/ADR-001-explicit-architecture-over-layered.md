# ADR-001: Adopt Explicit Architecture over Traditional Layered Architecture

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team
- **Reference**: [Herberto Graça — Explicit Architecture](https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/)

---

## Context

The Online Bookstore system handles distinct business domains (catalog, orders, notifications) with different scalability profiles, persistence needs, and team ownership. We need an architecture that:

- Keeps business logic independent of frameworks and infrastructure choices
- Allows infrastructure (database, cache, messaging) to be swapped without touching business rules
- Makes the system's intent explicit from the package structure alone
- Supports testability at every layer without spinning up a full Spring context

The traditional **layered architecture** (`controller → service → repository`) is the default choice in Spring Boot projects, but it suffers from:

1. **Framework leakage**: `@Transactional`, JPA annotations, and Spring stereotypes creep into the "service" layer, making business logic untestable without a Spring context.
2. **No enforced boundaries**: Nothing prevents a controller from calling a repository directly, or a service from importing another service's internal classes.
3. **Technology coupling**: Changing from JPA to JDBC, or adding a Redis cache layer, requires modifying the service layer.
4. **Hidden intent**: A package named `service/` tells you nothing about what the application actually does.

---

## Decision

We adopt **Explicit Architecture** — a synthesis of DDD, Hexagonal (Ports & Adapters), Onion, and Clean Architecture — as the structural pattern for every microservice.

The application core is divided into two layers:

### Domain Layer (innermost)
Contains the **what** of the business: Entities, Aggregates, Value Objects, Domain Events, and Domain Services. This layer has **zero dependencies** on any framework, library, or other layer. It is pure Java.

### Application Layer
Contains the **how** of the business use cases: Application Services implement use case interfaces (primary ports) and depend on repository/messaging interfaces (secondary ports). This layer depends only on the domain layer. No Spring annotations on use case classes.

### Interfaces Layer (outermost — driving side)
Contains *primary (driving) adapters*: REST controllers and inbound Kafka consumers. These receive external requests and translate them into Commands or Queries for the application layer. Spring MVC / Messaging annotations live here.

### Infrastructure Layer (outermost — driven side)
Contains *secondary (driven) adapters*: JPA repositories, Kafka producers, Redis clients, ElasticSearch adapters, outbound HTTP clients. These are called by the application layer through secondary ports. All framework and I/O code lives here.

**Dependency Rule**: Dependencies always point inward. The domain knows nothing about the application layer. The application layer knows nothing about infrastructure. Violations are enforced by ArchUnit tests.

---

## Package Structure

```
com.example.{service}/
├── domain/           ← zero deps: pure Java
├── application/      ← depends on domain only; no framework imports
├── infrastructure/   ← driven adapters: JPA, Kafka producers, Redis, HTTP clients
└── interfaces/       ← driving adapters: REST controllers, Kafka consumers
```

`interfaces/` and `infrastructure/` are both outer layers; both depend on `application/`. They are kept separate because they play opposite roles in the Ports & Adapters model — driving vs. driven.

---

## Consequences

### Positive
- **Testability**: Domain and application layers can be tested with plain JUnit 5, no Spring context, no Docker. Test suites run in seconds.
- **Framework independence**: Switching from Spring Boot to Quarkus, or from JPA to JOOQ, is a change confined to the `infrastructure/` package.
- **Explicit intent**: Package names (`command/`, `query/`, `port/out/`, `interfaces/`, `infrastructure/`) communicate the architectural role, not just the technical tier.
- **Enforced boundaries**: ArchUnit rules catch accidental imports across layers at build time.
- **Team scalability**: Clear ownership boundaries make it easy to assign teams to layers or bounded contexts.

### Negative
- **Higher initial boilerplate**: More interfaces, mappers, and packages compared to plain layered architecture.
- **Learning curve**: Developers unfamiliar with Hexagonal Architecture need onboarding time.
- **Indirection**: Tracing a feature end-to-end requires navigating more files (controller → port/in → usecase → port/out → persistence adapter).

### Mitigations
- CLAUDE.md documents the conventions explicitly.
- ADR-001 (this document) explains the rationale so developers understand the "why".
- The `simplify` skill is disabled for architecture-structural code — boilerplate here is intentional.
