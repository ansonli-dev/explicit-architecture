# Architecture Rules

Use these rules when generating Java 17 + Spring Boot 3 code for Explicit Architecture. Keep this file as decision guidance; load code templates from `assets/templates/implementation/` only when writing the corresponding code.

## Package Zones

Use the repository's existing package names when present. If creating from scratch, prefer:

```text
com.example.{context}/
├── domain/
│   ├── model/
│   ├── ports/
│   ├── event/
│   └── service/
├── application/
│   ├── command/{feature}/
│   ├── query/{feature}/
│   └── port/outbound/
├── infrastructure/
│   ├── persistence/
│   ├── client/
│   ├── messaging/
│   ├── cache/
│   └── config/
└── interfaces/
    ├── rest/
    ├── messaging/consumer/
    ├── event/
    └── job/
```

Only create packages that contain real code. Do not create empty placeholders.

## Implementation Template Routing

When generating production code, read only the needed template:

| Need | Template |
|---|---|
| Aggregate root with business invariants | `assets/templates/implementation/aggregate.java` |
| Value object | `assets/templates/implementation/value-object.java` |
| Domain event | `assets/templates/implementation/domain-event.java` |
| Write-side repository port | `assets/templates/implementation/repository-port.java` |
| Command + result + handler | `assets/templates/implementation/command-handler.java` |
| Query + result + handler | `assets/templates/implementation/query-handler.java` |
| Persistence adapter | `assets/templates/implementation/persistence-adapter.java` |
| REST controller | `assets/templates/implementation/rest-controller.java` |

Templates use `{{PLACEHOLDER}}` markers. Replace every marker with project-specific names and delete comments that are not useful in the final code.

## Domain Rules

Write:

- Aggregates, entities, value objects, enums, domain exceptions.
- Domain events as local facts.
- Write-side repository ports when the interface speaks domain language.
- Domain services for pure cross-aggregate logic.

Rules:

- No `jakarta.persistence`, Spring Data, web, messaging, cache, or client imports.
- No `@Entity`, `@Table`, relationship annotations, `@Transactional`, or framework proxy behavior.
- A domain service must not inject ports or adapters. Pass all required data as method parameters.
- Business guards belong in aggregate/domain methods, not handlers.
- Domain events must not import cross-boundary schema/generated transport classes.

## Application Rules

Write:

- `Command` records, command result records, command handlers.
- `Query` records, query result records, query handlers.
- Outbound ports for external clients, read models, search, cache, notifications, file storage, etc.

Command handler flow:

1. Load data through ports.
2. Call aggregate/domain service.
3. Save aggregate through write-side port.
4. Return command result from in-memory domain state.

Query handler flow:

1. Receive query.
2. Apply authorization/scope if the project has it.
3. Read via read model/search/projection port.
4. Return query result.

Rules:

- No persistence annotations, SDK clients, messaging libraries, web annotations, cache APIs, or raw technology imports.
- `@Service` on handlers is acceptable.
- No raw field injection.
- Command handlers do not return query result records or external response DTOs.
- Query handlers do not rehydrate domain aggregates unless the repository's architecture explicitly requires it.
- No direct event publishing from handlers when the project uses outbox or domain-event relay.

## Infrastructure Rules

Write:

- Persistence entities and repositories.
- Mappers between persistence models and domain models.
- Adapter implementations for application/domain ports.
- External clients, messaging adapters, cache adapters, file adapters, configuration classes.

Rules:

- Framework and technology details live here.
- Persistence entities must not leak outside infrastructure.
- Map explicitly between persistence models and domain/application DTOs.
- Put default transaction boundaries on persistence adapter methods.
- Never hold a transaction open across external I/O.
- Cache/search/index synchronization is infrastructure behavior unless it has business semantics.

## Interfaces Rules

Write:

- REST/RPC controllers.
- Message consumers.
- Schedulers/jobs/CLI adapters.
- Request and response DTOs.
- Same-process business event listeners that translate events to commands.

Rules:

- No business rules.
- No repository or infrastructure adapter calls.
- Dispatch command/query via the project's application entrypoint (`CommandBus`, `QueryBus`, or local equivalent).
- Map request DTO -> command/query and result -> response explicitly.
- Keep validation to syntactic/protocol input validation.

## Events and Cross-Boundary Contracts

Use this sequence:

1. Define local domain event.
2. Register/return it from aggregate behavior.
3. Define or evolve cross-boundary contract/schema.
4. Map local domain event to integration message in infrastructure.
5. Publish reliably through the project's chosen mechanism.
6. Consumer translates message to command.
7. Handler/domain model performs business response.

Rules:

- Domain event is local; integration message is external contract.
- Consumers must be idempotent.
- Breaking message/API changes require versioning or parallel change.
- Cross-service atomicity is not available; use eventual consistency, retries, compensation, and reconciliation.

## Planning Checklist

For every feature plan, require:

- Domain model changes.
- Command path changes.
- Query path changes.
- Ports and adapters.
- Interface adapters.
- Event/contracts if crossing boundaries.
- Data migration if storage shape changes.
- Tests by layer.

If a section does not apply, say "not needed" and why.

## Review Checklist

Reject or revise generated code if:

- Domain imports application/infrastructure/framework behavior.
- Handler contains business guard that belongs in domain.
- Controller/consumer calls repository or adapter directly.
- Persistence entity leaks into application/interfaces.
- Command handler returns query result or response DTO.
- Query handler rehydrates domain aggregate unnecessarily.
- Transaction wraps external I/O.
- Cross-boundary event is consumed without idempotency.
- Tests are only component/e2e and miss domain/application unit tests.
