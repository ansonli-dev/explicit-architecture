# Clarified Architecture

**A Pragmatic Refinement of Explicit Architecture**

> Based on DDD / Hexagonal / Onion / Clean / CQRS
>
> Version 1.0 · March 2026

---

## Table of Contents

- [Part I: Philosophy & Principles](#part-i-philosophy--principles)
  - [1.1 Why "Clarified"?](#11-why-clarified)
  - [1.2 The One Non-Negotiable Rule](#12-the-one-non-negotiable-rule)
  - [1.3 Guiding Principles](#13-guiding-principles)
- [Part II: Structural Overview](#part-ii-structural-overview)
  - [2.1 The Four Zones](#21-the-four-zones)
  - [2.2 Dependency Direction](#22-dependency-direction)
  - [2.3 Ports & Adapters Retained](#23-ports--adapters-retained)
- [Part III: Resolving the Five Tensions](#part-iii-resolving-the-five-tensions)
  - [3.1 Tension 1: One Home for Use Cases](#31-tension-1-one-home-for-use-cases)
  - [3.2 Tension 2: Domain Service Purity](#32-tension-2-domain-service-purity)
  - [3.3 Tension 3: Repository Interface Placement & CQRS Read Path](#33-tension-3-repository-interface-placement--cqrs-read-path)
  - [3.4 Tension 4: Shared Kernel Minimization](#34-tension-4-shared-kernel-minimization)
  - [3.5 Tension 5: Cross-Component Data Consistency](#35-tension-5-cross-component-data-consistency)
- [Part IV: Component Design](#part-iv-component-design)
  - [4.1 What is a Component?](#41-what-is-a-component)
  - [4.2 Inter-Component Communication Rules](#42-inter-component-communication-rules)
  - [4.3 Component Internal Structure (Recommended Directory Layout)](#43-component-internal-structure-recommended-directory-layout)
- [Part V: Flow of Control](#part-v-flow-of-control)
  - [5.1 Command Flow (Write Path)](#51-command-flow-write-path)
  - [5.2 Query Flow (Read Path)](#52-query-flow-read-path)
  - [5.3 Cross-Component Event Flow](#53-cross-component-event-flow)
  - [5.4 Transaction Boundary Rules](#54-transaction-boundary-rules)
- [Part VI: Scaling Guide by Project Size](#part-vi-scaling-guide-by-project-size)
- [Part VII: Enforcement & Testing Strategy](#part-vii-enforcement--testing-strategy)
  - [7.1 Architectural Fitness Functions](#71-architectural-fitness-functions)
  - [7.2 Testing Strategy by Zone](#72-testing-strategy-by-zone)
- [Part VIII: Anti-Patterns to Avoid](#part-viii-anti-patterns-to-avoid)
- [Part IX: Architecture Decision Records](#part-ix-architecture-decision-records)
- [Part X: Summary](#part-x-summary)

---

## Part I: Philosophy & Principles

### 1.1 Why "Clarified"?

Herberto Graça's Explicit Architecture is an outstanding conceptual map that fuses DDD, Hexagonal, Onion, Clean Architecture, and CQRS into a single mental model. It has become one of the most referenced architecture articles in the industry. However, when teams attempt to implement it, they consistently encounter five tension zones where the original article leaves the decision to the reader:

1. **Application Service vs Command Handler** — two containers for the same responsibility.
2. **Domain Service vs Application Service** — a boundary that shifts depending on who you ask.
3. **Repository interface placement** — Application layer or Domain layer?
4. **Shared Kernel growth** — the blob that eats all components.
5. **Cross-component data consistency** — eventual consistency without a safety net.

**Clarified Architecture** resolves each of these tensions with an opinionated, default-safe choice. The name reflects the intent: **we are not inventing a new architecture. We are clarifying the ambiguous joints in an existing, proven one.**

### 1.2 The One Non-Negotiable Rule

> **Iron Rule**
>
> The Domain Model depends on nothing outside itself. No infrastructure interfaces, no application-layer types. This is the single invariant that must hold across all project sizes, team structures, and deployment topologies. Every other architectural boundary exists to protect this one.

Everything else in this document is a default that can be relaxed when the project context demands it. But relaxing this rule is always a mistake. If your Entity imports a Repository, your architecture has already collapsed regardless of how many layers the folder structure claims to have.

> **On Framework Annotations**
>
> The Iron Rule prohibits **behaviorally invasive** framework annotations in the Domain Model and Domain Services zones: `@Entity`, `@Transactional`, `@Lazy`, JPA relationship annotations (`@OneToMany`, etc.), and any annotation that alters runtime behavior or creates framework-managed proxies. These change how objects are constructed, loaded, and flushed — all infrastructure concerns that pollute domain semantics.
>
> **Pure discovery annotations** (`@Component`, `@Service`) on Domain Services are acceptable in single-framework projects. They carry no runtime behavior, do not affect testability (`new OrderPricingService()` works identically with or without them), and simply inform the container's wiring. If the project requires portability across frameworks or enforces strict module isolation, these too should be removed and wiring delegated to the Application layer.

### 1.3 Guiding Principles

1. **Protect the inner circle, relax the outer circles.** The Domain Model is sacred. The Application layer is important. The Infrastructure layer is replaceable. Invest your architectural discipline accordingly.
2. **One home for each concept.** If two constructs serve the same purpose, eliminate one. Ambiguity in placement is worse than imperfect placement.
3. **Architecture complexity ≤ problem complexity.** If your architecture has more moving parts than the business problem it solves, you have over-engineered.
4. **Seams on demand.** Start with the simplest arrangement that preserves the Iron Rule. Add indirection (buses, event sourcing, CQRS) only when a concrete need emerges.
5. **Testability is the proof.** If a layer is hard to unit-test without mocking infrastructure, the boundary is in the wrong place.

---

## Part II: Structural Overview

### 2.1 The Four Zones

Clarified Architecture preserves the concentric-circle model from Onion Architecture but reduces it to four unambiguous zones, listed from innermost to outermost:

| Zone | Contains | Depends On | Key Constraint |
|------|----------|------------|----------------|
| **Domain Model** | Entities, Value Objects, Domain Events, Specifications, Enums | Nothing | No infrastructure/application imports; no behaviorally invasive framework annotations |
| **Domain Services** | Pure cross-entity business logic | Domain Model only | No I/O, no side effects; no behaviorally invasive annotations (`@Transactional`, etc.) |
| **Application (Use Cases)** | Handlers, Ports, DTOs, Application Events, ReadAuthorization | Domain Model + Domain Services | Orchestration only; no business rules |
| **Infrastructure + UI** | Adapters, Controllers, ORM configs, Messaging, External APIs | All inner zones (via Ports) | Implements Ports; never imported by inner zones |

> **Clarification vs Explicit Architecture**
>
> Graça's original model places Domain Services and Domain Model in the same layer. We split them into two zones because the constraint "no I/O" on Domain Services is the single most impactful boundary for testability and maintainability, and deserves to be an explicit, enforceable rule rather than a guideline.

### 2.2 Dependency Direction

All source-code dependencies point inward. This means:

- Infrastructure depends on Application (implements Ports).
- Application depends on Domain Services and Domain Model (calls pure logic, references entities).
- Domain Services depend on Domain Model (operates on entities and value objects).
- Domain Model depends on nothing.

At runtime, the flow reverses: a Controller (outer zone) invokes a Handler (application zone), which calls a Domain Service (inner zone) with pre-loaded entities (innermost zone), then uses a Repository adapter (outer zone, injected via Port) to persist changes.

### 2.3 Ports & Adapters Retained

Clarified Architecture fully retains the Ports & Adapters (Hexagonal) model:

- **Ports** are interfaces defined inside the Application zone. They describe what the application needs from the outside world (persistence, messaging, search, notifications) in the application's own language.
- **Driving Adapters** (Controllers, CLI commands, message consumers) sit in the UI/Infrastructure zone and translate external input into Port calls.
- **Driven Adapters** (Repository implementations, API clients, email senders) implement Ports and are injected into Handlers via dependency injection.

> **Port Design Rule**
>
> Ports are designed to fit the Application Core's needs, never to mirror an external tool's API. A port named `EmailSender` with a method `sendWelcomeEmail(UserId)` is correct. A port that exposes raw SMTP concepts (`setHeader`, `setBody`, `connect`) is an abstraction leak.

---

## Part III: Resolving the Five Tensions

### 3.1 Tension 1: One Home for Use Cases

**Problem:** Explicit Architecture allows use-case logic to live in either an Application Service or a Command Handler, creating two possible "homes" for the same responsibility. Teams end up with inconsistent placement and wasted decision energy.

**Clarified Resolution: Choose one container and enforce it project-wide.**

| If you have... | Then use... | Eliminate... |
|----------------|-------------|-------------|
| A Command/Query Bus | Handlers as the sole use-case container | Application Service as a concept |
| No Bus (simple project) | Application Services as the sole use-case container | Handler as a concept |

There is no scenario where both should coexist in new code. If you are migrating a legacy system that already uses Application Services and you are introducing a Bus, coexistence is a temporary state with an explicit migration deadline documented in an ADR.

**Handler anatomy (when using a Bus):**

1. Receive a Command or Query object.
2. Load required entities via Repository Ports.
3. Delegate pure business logic to the Domain Model or Domain Services.
4. Persist results via Repository Ports.
5. Dispatch Application Events.

If two Handlers share logic, extract it into a private method or a shared use-case fragment in the same Application zone. Do not re-introduce Application Service as a class.

> **Enforcement**
>
> Use static analysis (ArchUnit for Java/Kotlin, Deptrac for PHP, dependency-cruiser for TypeScript) to enforce: no class in the Application zone may be named `*Service` if a Bus is present; no class may be named `*Handler` if no Bus is present.

### 3.2 Tension 2: Domain Service Purity

**Problem:** The boundary between Domain Service and Application Service blurs when a Domain Service needs data it cannot fetch (because it must not depend on Repositories). This leads to "ping-pong" calls between layers.

**Clarified Resolution: Domain Services are pure functions. They receive all inputs as parameters and return results. They never hold Repository references, never trigger I/O, never dispatch events.**

> **Domain Service Contract**
>
> A Domain Service method signature must look like: `domainOperation(Entity a, Entity b, ValueObject rate) → DomainResult`. If you see a Repository or Port in a Domain Service constructor, it belongs in the Handler.

**The Prefetch-All Pattern:**

The Handler is responsible for all I/O. It loads every entity and value object the Domain Service will need, passes them in, receives the result, and then persists/dispatches.

| Step | Responsible Zone | Action |
|------|------------------|--------|
| 1 | Handler (Application) | Parse command, load AccountA, AccountB, ExchangeRate from Repositories |
| 2 | Domain Service | `transfer(accountA, accountB, rate) → TransferResult` (pure computation) |
| 3 | Handler (Application) | Persist updated accounts, dispatch TransferCompleted event |

**What if the Domain Service needs data conditionally?**

If step 2 reveals that additional data is needed (e.g., a compliance check for transfers above $10,000), the Handler should be structured in two phases:

1. **Phase 1:** Handler loads initial entities → Domain Service computes and returns an intermediate result (e.g., `RequiresComplianceCheck`).
2. **Phase 2:** Handler loads compliance data → Domain Service completes the operation.

This is not a workaround; it is the correct separation of concerns. I/O decisions live in the Handler. Pure logic lives in the Domain Service. The phase boundary makes this explicit.

> **Testing Payoff**
>
> Pure Domain Services are tested with zero mocks: `new TransferService().transfer(fakeA, fakeB, fakeRate)`. Test execution is instant, deterministic, and completely isolated from infrastructure.

### 3.3 Tension 3: Repository Interface Placement & CQRS Read Path

**Problem:** Graça places Repository interfaces in the Application Layer alongside ORM and messaging interfaces. This conflates two things: Repositories (a domain concept) and persistence abstraction (an infrastructure concern). Additionally, the CQRS read path bypasses the Domain layer entirely, leaving no clear place for data-access policies.

**Clarified Resolution: Split the write path and read path cleanly, and place each interface where it semantically belongs.**

**Write path (Command side):**

- **Repository interfaces belong in the Domain Model zone.** `OrderRepository` is a domain concept ("the collection of all Orders"). It speaks the domain's language: `findByCustomer(CustomerId)`, `save(Order)`.
- **Repository implementations** belong in the Infrastructure zone. They implement the domain interface using ORM, raw SQL, or any persistence technology.
- **No dual-layer abstraction.** There is no separate "Persistence Interface" wrapping the ORM. One interface (Repository) and one implementation (e.g., `MySqlOrderRepository`) is sufficient for 95% of projects. Add the ORM abstraction layer only if you have a concrete plan to swap ORMs — not as a speculative precaution.

**Read path (Query side):**

- **Query objects live in the Application zone.** They bypass the Domain Model and go straight to the database, returning DTOs.
- **ReadAuthorization interface** sits in the Application zone as a thin gate before Query execution. It enforces "who can see what" without involving domain entities.
- **Query → DTO → ViewModel → View.** The ViewModel may contain presentation logic (formatting dates, computing display labels) but no business rules.

| Path | Interface Location | Implementation Location | Passes Through Domain? |
|------|--------------------|------------------------|----------------------|
| Write (Command) | Domain Model zone | Infrastructure zone | Yes — entities enforce invariants |
| Read (Query) | Application zone | Infrastructure zone | No — direct DB query to DTO |

> **ReadAuthorization Contract**
>
> ReadAuthorization is not a full ACL system. It is a single method: `canAccess(QueryContext, ResourceScope) → Boolean`. It is called by the Handler before executing the Query. If it fails, the Handler throws an authorization exception. The Query itself has no knowledge of permissions.

### 3.4 Tension 4: Shared Kernel Minimization

**Problem:** The Shared Kernel in Explicit Architecture becomes a gravity center. Every cross-component communication need adds event classes, specification objects, and shared value objects. Over time, it becomes the largest and most volatile module, coupling all components indirectly.

**Clarified Resolution: Replace the Shared Kernel code library with an Event Registry — a schema-only artifact containing no executable code.**

**Three rules govern the Event Registry:**

1. **Event ownership belongs to the publisher.** The `OrderPlaced` event class exists only inside the Order component. No other component may import it directly.
2. **The registry contains schemas, not classes.** Each event is described by a name and a list of typed fields (JSON Schema, Protobuf, or Avro). The registry is versioned in source control alongside the codebase.
3. **Consumers build their own DTOs.** The Billing component receives a raw `OrderPlaced` message and deserializes it into a `BillingOrderPlacedDTO` that contains only the fields Billing cares about. This is the Anti-Corruption Layer pattern applied at the event boundary.

**Schema evolution governance:**

- **Backward-compatible changes only:** adding new optional fields is allowed. Removing or renaming fields requires a new event version (e.g., `OrderPlacedV2`).
- **CI-enforced compatibility check:** a schema registry validation step in the build pipeline rejects any change that would break existing consumers.
- **Deprecation lifecycle:** when a new event version is published, the old version enters a deprecation window (e.g., 90 days). After the window, the old schema is removed and consumers must have migrated.

| Aspect | Graça's Shared Kernel | Clarified Event Registry |
|--------|----------------------|--------------------------|
| Contains | Event classes, specifications, shared VOs | Event schemas (name + typed fields) only |
| Executable code | Yes | No |
| Change impact | Recompile all components | Only if schema contract is broken |
| Language coupling | Same programming language required | Language-agnostic (JSON/Protobuf) |
| Growth tendency | Unbounded | Bounded by event count, not code volume |

> **What Goes in the Registry**
>
> Only integration events (those that cross component boundaries). Domain Events that are internal to a component (e.g., `OrderLineItemAdded` used only within the Order component) do **not** appear in the registry. They are private to their component.

### 3.5 Tension 5: Cross-Component Data Consistency

**Problem:** When components need each other's data, Explicit Architecture offers two patterns (shared storage with read-only queries, or segregated storage with event-synced local copies) but does not discuss the consistency, failure, and evolution implications of either choice.

**Clarified Resolution: Choose the pattern based on deployment topology, and always pair eventual consistency with a compensating mechanism.**

**Pattern A: Modular Monolith (single deployable)**

- **Use database Views as the read contract.** Component A owns its tables and exposes a SQL View (or materialized view) for data that other components may read. Component B queries the View, never the raw tables.
- **View schema is a contract.** A may freely change its internal table schema as long as the View contract is maintained. This decouples internal storage evolution from cross-component reads.
- **No event-synced local copies needed.** In a single-database monolith, strong consistency is available for free. Using eventual consistency here adds complexity without benefit.

**Pattern B: Microservices (multiple deployables)**

- **Local copies are mandatory.** Each service maintains a read-only projection of the data it needs from other services, updated via integration events.
- **Three required companion mechanisms:**
  1. **Idempotent event handlers:** processing the same event twice produces the same result. Implement using a processed-event-ID table or natural idempotency.
  2. **Compensating transactions:** every state change that depends on eventually-consistent data must have a defined rollback or correction path.
  3. **Periodic reconciliation:** a scheduled job (e.g., nightly) compares local copies against the source of truth and emits correction events for any drift.

> **Choosing the Wrong Pattern is Costly**
>
> Using Pattern B (event-synced local copies) inside a monolith is over-engineering. Using Pattern A (shared database views) across microservices creates hidden coupling. Match the pattern to the deployment topology.

---

## Part IV: Component Design

### 4.1 What is a Component?

A component is a coarse-grained unit of code organized around a business sub-domain. Examples: Order, Billing, Inventory, UserManagement. Components are the primary axis of code organization (package-by-component), cutting across layers.

Each component contains its own:

- Domain Model zone (entities, value objects, domain events, repository interfaces).
- Domain Services zone (pure cross-entity logic).
- Application zone (handlers, commands, queries, ports, DTOs).
- Infrastructure zone (repository implementations, adapter implementations).

Components do not share domain-model types. If two components both need a concept like "Monetary Amount," each defines its own Value Object. Shared low-level utilities (like a Money type) can exist in a tiny shared library, but this is a code-sharing decision, not an architecture decision.

### 4.2 Inter-Component Communication Rules

| Communication Type | Mechanism | Coupling Level |
|--------------------|-----------|---------------|
| Trigger logic in another component | Integration Event via Event Bus | Low (async, schema-only contract) |
| Trigger logic that requires immediate response | Synchronous call via Discovery Service or internal API | Medium (requires availability) |
| Read another component's data (monolith) | Database View owned by source component | Low (schema contract only) |
| Read another component's data (microservices) | Local copy synced via integration events | Low (eventual consistency) |

**Rule:** A component may never directly import a class from another component's Domain Model, Domain Services, or Application zone. The only shared artifact is the Event Registry schema.

### 4.3 Component Internal Structure (Recommended Directory Layout)

The following layout applies to each component, using a typical Java/Kotlin/TypeScript project as reference:

| Directory | Zone | Contains |
|-----------|------|----------|
| `component/domain/model/` | Domain Model | Entities, Value Objects, Enums, Domain Events |
| `component/domain/ports/` | Domain Model | Repository interfaces (write-side) |
| `component/domain/services/` | Domain Services | Pure cross-entity logic classes |
| `component/application/commands/` | Application | Command classes + their Handlers |
| `component/application/queries/` | Application | Query classes + their Handlers + DTOs |
| `component/application/ports/` | Application | Driven port interfaces (email, search, etc.) |
| `component/application/events/` | Application | Application Event dispatching |
| `component/infrastructure/persistence/` | Infrastructure | Repository implementations, ORM mappings |
| `component/infrastructure/adapters/` | Infrastructure | External API clients, email senders, etc. |
| `component/ui/rest/` | UI / Infrastructure | REST controllers (driving adapters) |
| `component/ui/cli/` | UI / Infrastructure | CLI commands (driving adapters) |

---

## Part V: Flow of Control

### 5.1 Command Flow (Write Path)

A typical write operation follows this sequence:

1. **Controller** receives HTTP request, validates input format, constructs a Command DTO.
2. **Command Bus** routes the Command to its registered Handler.
3. **Handler** loads required entities via Repository Ports (driven adapters injected at construction).
4. **Handler** calls Domain Service or Entity methods to execute business logic (pure computation, no I/O).
5. **Handler** persists modified entities via Repository Ports.
6. **Handler** dispatches Application Events (e.g., `OrderPlaced`) to the Event Bus.
7. **Event listeners** in other components (or the same component) react to the event asynchronously.

### 5.2 Query Flow (Read Path)

A typical read operation follows this sequence:

1. **Controller** receives HTTP request, constructs a Query DTO.
2. **Query Handler** calls ReadAuthorization to verify access.
3. **Query Handler** executes an optimized database query (may use raw SQL, a View, or a read-optimized projection).
4. **Query Handler** returns a flat DTO (no domain entities).
5. **Controller** wraps the DTO in a ViewModel if needed, returns the response.

Note: the read path deliberately does not load entities or invoke domain logic. Its sole purpose is to return data efficiently. Business rules are enforced on the write path; the read path trusts that persisted data is already valid.

### 5.3 Cross-Component Event Flow

1. **Component A's Handler** dispatches an integration event (e.g., `OrderPlaced`).
2. **Event Bus** delivers the event to all registered listeners.
3. **Component B's Event Listener** deserializes the event payload into its own DTO (Anti-Corruption Layer).
4. **Component B's Listener** constructs an internal Command and dispatches it to B's own Bus.
5. **Component B's Handler** processes the Command using its own domain logic.

> **Event Listener Discipline**
>
> An event listener should do one thing: translate an external event into an internal command. It should never contain business logic. If the listener is longer than 10 lines, logic is leaking out of the handler.

### 5.4 Transaction Boundary Rules

Transactions are an infrastructure concern. The rules below govern where `@Transactional` belongs and how external I/O interacts with transaction boundaries.

#### Default: transaction on the Persistence Adapter

For the common case — one Handler, one aggregate root — place `@Transactional` on the Persistence Adapter's `save()` method. The transaction scope is as tight as possible, and all external I/O in the Handler naturally falls outside it.

```
Handler.handle():
  ① externalClient.fetchData()     ← no transaction yet
  ② aggregate = Aggregate.create() ← in-memory
  ③ repository.save(aggregate)     ← @Transactional opens and commits here
  ④ emailSender.send(...)          ← transaction already committed
```

#### External I/O and transaction boundaries

Two categories of external calls require different treatment:

**Pre-fetch calls (read-only, before business logic):** Call before any persistence operation. Because `@Transactional` is on the Persistence Adapter, no DB connection is held during the network call. No special handling needed.

**Post-commit side effects (email, push notification, webhook):** Must execute *after* the DB transaction commits. Two mechanisms are available; choose based on the nature of the side effect:

**Direct call in Handler (after `save()`):** When `@Transactional` is on the Persistence Adapter, `save()` commits before returning. Any call after it in the Handler is naturally post-commit. Use this when:
- The side effect is the **primary purpose** of the command (e.g., `SendNotificationCommandHandler` — sending the email *is* the point).
- The caller needs to know whether the side effect succeeded (synchronous response required).
- There is exactly one side effect and no reason to decouple it.

**Domain Event + `@TransactionalEventListener(phase = AFTER_COMMIT)`:** The aggregate registers a Domain Event on state change; a listener fires only after the transaction commits. Use this when:
- The side effect is a **reaction** to a state change, not the primary purpose of the command.
- **Multiple things** need to respond to the same state change independently.
- The side effect should be **decoupled** from the Handler so that new reactions can be added without modifying it.
- The side effect crosses a **service boundary** (in which case the Domain Event becomes an integration event via the Outbox — see §5.3).

**Infrastructure-only side effects (cache invalidation, search index update):** These carry no business semantics and do not belong in either the Handler or a Domain Event listener. Place them inside the Persistence Adapter's `save()` method so the Handler remains unaware of them.

| Side effect type | Mechanism |
|------------------|-----------|
| Primary purpose of the command | Direct call in Handler after `save()` |
| Reaction to state change, same service, single consumer | Direct call or Domain Event (prefer simpler) |
| Reaction to state change, same service, multiple consumers | Domain Event + `@TransactionalEventListener` |
| Cross-service reaction | Domain Event → Integration Event via Outbox (mandatory) |
| Infrastructure operation (cache, index) | Inside Persistence Adapter `save()` |

> **Rule: never include external I/O (HTTP calls, email sends, message publishes) inside a transaction boundary.** Holding a DB connection across a network call exhausts the connection pool under load. It also makes the transaction boundary semantically incorrect: a rollback cannot undo an email already sent.

#### Cross-aggregate atomicity (same database)

When a single use case must update two aggregate roots atomically in the same database, place `@Transactional` on the CommandHandler to span both `save()` calls:

```
@Transactional   ← on Handler
Handler.handle():
  ① load AggregateA, AggregateB
  ② domainService.coordinate(a, b)  ← pure computation
  ③ repoA.save(a)
  ④ repoB.save(b)                   ← same transaction as ③
```

This is an explicit architectural decision. Document it in an ADR. Do not do it by default — first ask whether the aggregate boundary is drawn correctly. Two objects that must always change together are often the same aggregate.

> **`@Transactional` must never appear on a Domain Service.** Domain Services are pure functions with no infrastructure dependency. `@Transactional` is a behaviorally invasive annotation that generates a Spring proxy and introduces a hidden dependency on `PlatformTransactionManager`. A Domain Service annotated with `@Transactional` cannot be tested without a Spring context, and its transactional behavior is invisible to callers. Transaction scope is an orchestration decision; it belongs in the Handler.

#### Cross-service atomicity (different databases)

DB-level atomicity is impossible across service boundaries. Use the **Saga pattern**: each step is an independent atomic operation on its own aggregate; failures are handled by compensating transactions triggered via domain events.

```
PlaceOrderHandler:
  save Order(PENDING) + outbox(OrderPlaced)  ← atomic commit

Catalog consumes OrderPlaced:
  reserve stock → emit StockReserved or StockReservationFailed

Order consumes StockReserved:
  confirm order  ← atomic commit

Order consumes StockReservationFailed:
  cancel order   ← compensating transaction
```

The Outbox Pattern already provides the atomicity primitive for each step: state change + event publication are written in the same transaction and delivered at-least-once.

---

## Part VI: Scaling Guide by Project Size

Not every project needs the full ceremony. Clarified Architecture is designed to scale up and down. The following table describes what to adopt at each stage:

| Aspect | Small (1–3 people, < 1yr) | Medium (5–15 people, long-lived) | Large (multi-team, platform) |
|--------|---------------------------|----------------------------------|------------------------------|
| Use-case container | Application Service (direct call) | Handler via Command Bus | Handler via Command Bus |
| Domain Services | Inline in Application Service | Separate pure classes | Separate pure classes |
| Repository interface | In Domain Model zone | In Domain Model zone | In Domain Model zone |
| CQRS separation | Optional (single model OK) | Yes, separate Command/Query | Yes, possibly separate DBs |
| Command/Query Bus | No (direct injection) | Yes (sync dispatch) | Yes (async capable) |
| Event Bus | No (direct method call) | Yes (in-process) | Yes (distributed, e.g. Kafka) |
| Event Registry | Not needed | JSON schema in repo | Protobuf/Avro with CI checks |
| Cross-component data | Direct import OK | Database Views | Local copies + reconciliation |
| Shared Kernel | Allowed (small) | Event Registry only | Event Registry only |
| Static analysis | Optional | Recommended | Required (CI-enforced) |

> **Migration Path**
>
> Start at the Small column. When the team or codebase outgrows it, move one column to the right. The Iron Rule (Domain Model depends on nothing) holds at every stage. All other decisions are about how much indirection to add around the Domain Model.

---

## Part VII: Enforcement & Testing Strategy

### 7.1 Architectural Fitness Functions

Rules that are not enforced by tooling will be violated. Clarified Architecture prescribes the following fitness functions, executable as part of the CI pipeline:

| Rule | Tool Example | Check |
|------|-------------|-------|
| Domain Model has zero external imports | ArchUnit / Deptrac / dep-cruiser | No import from application, infra, or framework packages |
| Domain Services have no injected Ports | ArchUnit / Deptrac / dep-cruiser | Constructor parameters are only Domain Model types |
| Handlers do not contain business rules | Code review checklist (manual) | Handlers only orchestrate; if/else on business conditions → extract to Domain |
| No cross-component domain imports | ArchUnit / Deptrac / dep-cruiser | Component A's domain package is not imported by Component B |
| Event schema backward compatibility | Schema Registry CI plugin | No field removal or type change without version bump |

### 7.2 Testing Strategy by Zone

| Zone | Test Type | Dependencies | Speed Target |
|------|-----------|-------------|-------------|
| Domain Model | Unit tests | None (no mocks) | < 1ms per test |
| Domain Services | Unit tests | None (pass in-memory entities) | < 1ms per test |
| Handlers | Integration tests | In-memory repo / test doubles for ports | < 100ms per test |
| Infrastructure adapters | Integration tests | Real DB (testcontainers) or sandbox APIs | < 1s per test |
| End-to-end (per component) | Acceptance tests | Full stack, isolated component | < 5s per test |
| Cross-component | Contract tests | Pact or schema-level verification | < 500ms per test |

**Key insight:** The Domain Model and Domain Services zones should have the highest test count and the fastest execution. If your test pyramid is inverted (more integration tests than unit tests), the boundary between pure logic and I/O is probably in the wrong place.

---

## Part VIII: Anti-Patterns to Avoid

| Anti-Pattern | Symptom | Clarified Remedy |
|-------------|---------|------------------|
| Repository in Domain Service | Domain Service constructor takes a Repository interface | Move the fetch to Handler; pass entities as parameters |
| Dual use-case containers | Some use cases in Services, some in Handlers | Pick one per project; enforce via static analysis |
| Fat Shared Kernel | Shared library has 50+ classes; every change triggers full rebuild | Shrink to Event Registry (schemas only) |
| Persistence abstraction over-layering | Repository Interface + Persistence Interface + ORM Adapter = 3 layers for a simple `save()` | One Repository Interface + one implementation is enough |
| Anemic Domain Model with fat Handlers | Entities are data bags; all logic lives in Handlers | Push business rules into Entity methods; Handler only orchestrates |
| Read path through Domain Model | Query handlers load entities just to extract fields | Use direct SQL/View query returning DTO; skip entity loading |
| Event listener with business logic | Listener does complex processing, not just translation | Listener constructs a Command; dispatch to own Handler for processing |
| Speculative abstraction | Interface with exactly one implementation, no plan for alternatives | Remove the interface; add it when a second implementation is needed |
| Behaviorally invasive annotations in Domain | `@Entity`, `@Transactional`, or JPA relationship annotations on Domain Model classes or Domain Services | Replace with a separate JPA entity in the Infrastructure zone; map explicitly to/from the domain object |

---

## Part IX: Architecture Decision Records

Every project adopting Clarified Architecture should document its choices in Architecture Decision Records (ADRs). The following template captures the essential information:

| ADR | Content |
|-----|---------|
| **ADR-001: Use-case container choice** | We use Command Handlers as the sole use-case container because we have adopted a Command Bus for async capability. |
| **ADR-002: Domain Service purity** | Domain Services are pure functions with no I/O. All data loading happens in Handlers (Prefetch-All pattern). |
| **ADR-003: Repository interface placement** | Write-side Repository interfaces reside in the Domain Model zone. Read-side Query objects reside in the Application zone. |
| **ADR-004: Inter-component communication** | Components communicate via integration events described in the Event Registry. Direct cross-component class imports are prohibited. |
| **ADR-005: Data consistency strategy** | We use database Views for cross-component reads (monolith topology). Eventual consistency will be adopted only if we migrate to microservices. |

ADRs are living documents. When a decision is revisited, the original ADR is marked as superseded by the new one, preserving the decision history.

---

## Part X: Summary

Clarified Architecture is not a new invention. It is a set of opinionated defaults applied to the ambiguous joints in Explicit Architecture. The core thesis is:

> **Core Thesis**
>
> Protect the Domain Model with maximum rigor. Simplify everything else with pragmatic, context-appropriate defaults. When in doubt, choose the option that makes the Domain Model easier to test in isolation.

**The five clarifications in one sentence each:**

1. **One home for use cases:** Handler if you have a Bus, Application Service if you don't. Never both.
2. **Pure Domain Services:** all inputs as parameters, no I/O, no side effects.
3. **Repository in Domain, Query in Application:** write-side interfaces are domain language; read-side objects are application concerns.
4. **Event Registry over Shared Kernel:** schemas only, no executable code, backward-compatible evolution.
5. **Match consistency to topology:** Views for monoliths, local copies with reconciliation for microservices.

**The architecture's complexity should never exceed the complexity of the problem it solves.** Start simple, add seams on demand, and let the Iron Rule guide every decision.
