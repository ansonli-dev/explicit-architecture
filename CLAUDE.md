# CLAUDE.md — Explicit Architecture Demo

This file provides guidance for Claude Code when working in this repository. Read it fully before making changes.

> **Authority chain:** `docs/architecture/clarified-architecture/clarified-architecture-en.md` is the canonical source for architectural principles (highest authority). `docs/architecture/architecture-spec.md` translates those principles into project-specific naming and structure rules. This file (`CLAUDE.md`) provides Claude Code operational guidance. In any conflict, the higher-authority document wins.

---

## Project Purpose

This is a **demo project** implementing Explicit Architecture (DDD + Hexagonal + Onion + Clean + CQRS) across three Java microservices. Every architectural decision is intentional and educational. Do not simplify or collapse layers — the layer separation is the point.

---

## Module Responsibilities

### Modules

| Module | Port | Database | Cache/Search | Role |
|---|---|---|---|---|
| `catalog` | 8081 | PostgreSQL (`catalog`) | Redis | Microservice |
| `order` | 8082 | PostgreSQL (`order`) + ElasticSearch | — | Microservice |
| `notification` | 8083 | PostgreSQL (`notification`) | — | Microservice |
| `shared-events` | — | — | — | Kafka event schema contracts (Avro) |
| `seedwork` | — | — | — | Reusable DDD + CQRS base abstractions |

### `seedwork` — Shared Framework Library

Referenced by all three services. Each service resolves `com.example:seedwork:0.1.0` from **mavenLocal** — run `cd seedwork && ./gradlew publishToMavenLocal` once after any seedwork change. `shared-events` follows the same pattern: `cd shared-events && ./gradlew publishToMavenLocal`.

| Package | Contents |
|---|---|
| `com.example.seedwork.domain` | `DomainId<T>`, `AggregateRoot<ID>`, `DomainEvent`, `DomainException`, `NotFoundException` |
| `com.example.seedwork.application.command` | `Command<R>`, `CommandHandler<C,R>` |
| `com.example.seedwork.application.query` | `Query<R>`, `QueryHandler<Q,R>` |
| `com.example.seedwork.application.bus` | `CommandBus`, `QueryBus` interfaces |
| `com.example.seedwork.infrastructure.bus` | `SpringCommandBus`, `SpringQueryBus` (auto-discovery + logging + metrics) |
| `com.example.seedwork.infrastructure.jpa` | `AbstractAggregateRootEntity` (domain event bridge for Spring Data) |
| `com.example.seedwork.infrastructure.outbox` | `OutboxMapper` SPI, `OutboxWriteListener`, `KafkaOutboxEventPublisher`, `OutboxRelayScheduler` |
| `com.example.seedwork.infrastructure.kafka` | `KafkaMessageProcessor`, `IdempotentKafkaListener`, `RetryableKafkaHandler`, `ProcessedEventStore` |
| `com.example.seedwork.infrastructure.kafka.retry` | `RetryEntryProcessor`, `RetryScheduler`, `RetryProperties` |
| `com.example.seedwork.infrastructure.web` | `GlobalExceptionHandler` (400/404/405/406/415/422/500), `CorrelationIdFilter` |

**Rule:** `seedwork` must never contain service-specific business logic or domain concepts.

### infrastructure module

`infrastructure/` **只提供能力，不包含任何服务特定配置**。

| 归属 infrastructure | 归属各微服务 |
|---|---|
| Helm Chart（中间件实例） | Helm Chart（Deployment / Service / HPA） |
| `db/init.sql`（创建库和用户） | Flyway 迁移脚本（业务表） |
| Kafka Broker | Kafka Topic / Consumer Group 声明 |
| Redis Server | Redis 缓存键设计与 TTL 策略 |
| ElasticSearch 集群 | ElasticSearch 索引 Mapping 与生命周期 |
| OTel Collector / Prometheus / Grafana / Jaeger | 业务专属 Dashboard |
| Istio Ingress Gateway + 全局 mTLS | VirtualService / DestinationRule / NetworkPolicy 放行规则 |
| Helm 伞形 Chart（引用各服务 Chart） | — |

> 邮件通知：本地开发用日志模拟（`LogEmailAdapter`），**不使用 MailHog 或任何 SMTP 服务**。

---

## Strict Architecture Rules

### The Iron Rule

> **The Domain Model depends on nothing outside itself.** No infrastructure interfaces, no application-layer types, no framework annotations with runtime behavior. This invariant must hold at all times and cannot be relaxed.

### Layer Dependency Rule (ENFORCE ALWAYS)
```
Interfaces   ──┐
               ├──► Application ──► Domain
Infrastructure ─┘
```

- **`domain` packages:** zero framework imports — pure Java. Only **behaviorally invasive** annotations are prohibited: `@Entity`, `@Transactional`, JPA relationship annotations (`@OneToMany`, etc.). These alter runtime behavior and pollute domain semantics. Pure discovery markers (`@Component`, `@Service`) are acceptable on Domain Services in a single-framework project — they carry no runtime behavior and do not affect testability (`new MyDomainService()` works identically with or without them).
- **`application` packages:** no JPA/Redis/Kafka imports. `@Service` on CommandHandlers/QueryHandlers is acceptable; Lombok (`@Slf4j`, `@RequiredArgsConstructor`) is acceptable. No raw `@Autowired`.
- **`infrastructure` packages:** driven adapters — JPA, Redis, Kafka producers, HTTP clients. `@Transactional` lives here, not in handlers.
- **`interfaces` packages:** driving adapters — REST controllers, Kafka consumers. No business logic.

### Package Structure (per service)

```
com.example.{service}/
├── domain/                        ← depends only on seedwork.domain; zero framework deps
│   ├── model/                     ← Aggregates extend AggregateRoot<ID>, IDs implement DomainId<T>
│   │                                 Value Objects: use records (immutable, structural equality)
│   ├── ports/                     ← Write-side Repository interfaces (domain language; domain objects use domain types, not raw String/UUID)
│   │                                 e.g. OrderPersistence, BookPersistence, NotificationRepository
│   ├── event/                     ← Domain Events implement DomainEvent (records with eventId+occurredAt)
│   └── service/                   ← Domain Services (cross-aggregate, stateless pure logic)
├── application/                   ← depends on domain + seedwork.application; no JPA/Kafka/Redis imports
│   ├── port/
│   │   └── outbound/              ← Secondary Ports that are NOT domain concepts:
│   │                                 {Target}Client (HTTP), {Aggregate}SearchRepository (ES),
│   │                                 {Aggregate}Cache (Redis), {Aggregate}ReadRepository (JPA projections)
│   ├── command/                   ← CommandHandler<C,R> implementations
│   │   └── {aggregate}/           ← Command record + @Service CommandHandler + Result record (package-by-feature)
│   └── query/                     ← QueryHandler<Q,R> implementations
│       └── {aggregate}/           ← Query record + @Service QueryHandler + Read Model Result (package-by-feature)
├── infrastructure/                ← depends on application + domain + all frameworks; @Transactional lives here
│   ├── repository/
│   │   ├── jpa/                   ← JPA entities + Spring Data repos + persistence adapter
│   │   └── elasticsearch/         ← ES documents + Spring Data ES repos + search adapter (order only)
│   ├── messaging/
│   │   └── outbox/                ← OutboxMapper implementation (one per service)
│   ├── cache/                     ← Redis adapters (catalog only)
│   ├── email/                     ← Email adapters (notification only, log simulation)
│   └── client/                    ← HTTP clients for outbound calls (implements {Target}Client)
└── interfaces/                    ← Primary adapters (driving side); no business logic
    ├── rest/                      ← REST controllers; dispatch via CommandBus / QueryBus
    │   ├── request/               ← HTTP request DTOs ({Aggregate}XxxRequest records)
    │   └── response/              ← HTTP response DTOs ({Aggregate}XxxResponse records); map from *Result
    └── messaging/consumer/        ← Kafka consumers (only in services that consume events)
```

> **Why write-side Repository ports live in `domain/ports/`:** `OrderPersistence` is a domain concept — "the collection of all Orders." Parameters that represent domain objects use domain types (`OrderId`, `Order`), not raw `UUID` or `String`; primitive types (`int`, `String`) for simple values like pagination or filter strings are fine. Placing it in the application layer hides this semantic ownership. Client ports (`CatalogClient`), cache ports (`BookCache`), and search ports (`OrderSearchRepository`) are infrastructure abstractions with no domain meaning — they belong in `application/port/outbound/`.

> **Why `interfaces/` is separate from `infrastructure/`**: REST controllers are *primary (driving) adapters* — they receive requests and drive the application. JPA repositories and Kafka producers are *secondary (driven) adapters* — they are driven by the application. Conflating both in `infrastructure/` obscures this fundamental distinction from Hexagonal Architecture.

> **CommandBus / QueryBus as primary ports**: Instead of per-use-case interfaces, a single `CommandBus` and `QueryBus` act as the entry point for the driving side. Controllers depend only on these two interfaces and never import concrete handler classes. Cross-cutting concerns (logging, timing) live in `infrastructure/bus/` — handlers stay focused on business logic.

Only include the adapter packages a service actually uses. Do NOT create empty adapter packages as placeholders.

### Command Result vs Query Result vs HTTP Response

There are three distinct layers of data representation; each has a different lifecycle:

| Layer | Type | Location | Purpose |
|------|------|----------|---------|
| Command Handler output | `{Action}{Aggregate}Result` | `application/command/{aggregate}/` | In-memory domain state, zero extra IO |
| Query Handler output | `{Aggregate}{Purpose}Result` | `application/query/{aggregate}/` | Read model projection from DB/ES |
| HTTP API contract | `{Aggregate}{Purpose}Response` | `interfaces/rest/response/` | External API shape; may include HATEOAS links, versioning |

**Command Handlers must not return Query-layer Results.** Command and query paths are independent — coupling them creates bidirectional dependencies between write and read sides.

Controllers map explicitly: `*Result` → `*Response`. This decouples the API lifecycle (client-facing versioning, HATEOAS) from the business lifecycle (domain model evolution).

`PlaceOrderResult` and `OrderDetailResult` may have similar fields but are separate types. `PlaceOrderResult` is assembled from in-memory domain state — **zero extra IO**. `OrderDetailResult` may require a DB/ES read.

### Business Logic Placement

**Handlers orchestrate; the Domain decides.** An `if/else` or `throw` based on a business condition (e.g., "insufficient stock," "order already cancelled") belongs in the Domain Model or Domain Service, not in a Handler. The Handler's role is:

1. Load entities via Repository Ports
2. Call Domain Model / Domain Service with pre-loaded data
3. Persist results
4. Dispatch post-commit side effects if needed

If a Handler contains `if (businessCondition) throw new BusinessException(...)`, that check should move to a domain method.

### Domain Events vs Integration Events

| Type | Where it lives | Scope | Example |
|---|---|---|---|
| **Domain Event** | `domain/event/` | In-process; owned by aggregate; triggers outbox write | `OrderPlaced` |
| **Integration Event** | `shared-events/` (Avro schema) | Cross-service via Kafka; schema-contract | `com.example.events.v1.OrderPlaced` |

The Kafka publishing flow is handled transparently via the Outbox Pattern in seedwork:
```
Aggregate.someAction()  →  registers DomainEvent internally
    PersistenceAdapter.save(aggregate)
        → pulls domain events via aggregate.pullDomainEvents()
        → attaches to AbstractAggregateRootEntity
        → OutboxWriteListener (BEFORE_COMMIT) writes outbox row atomically
            → OutboxRelayScheduler polls outbox → KafkaOutboxEventPublisher sends to Kafka
```
- CommandHandlers **do not** call an `EventDispatcher` directly — publishing is a side-effect of persistence.
- Services implement `OutboxMapper` (one class per service in `infrastructure/messaging/outbox/`) to map domain events to Avro payloads.
- Domain objects never import `shared-events` Avro classes; only `OutboxMapper` does.

### Naming Conventions

| Concept | Location | Naming Pattern | Example |
|---|---|---|---|
| Command record | `application/command/{aggregate}/` | `{Action}{Aggregate}Command` | `PlaceOrderCommand` |
| CommandHandler | `application/command/{aggregate}/` | `{Action}{Aggregate}CommandHandler` | `PlaceOrderCommandHandler` |
| Command Result | `application/command/{aggregate}/` | `{Action}{Aggregate}Result` | `PlaceOrderResult` |
| Query record | `application/query/{aggregate}/` | `{Criteria}{Aggregate}Query` | `GetOrderQuery`, `ListOrdersQuery` |
| QueryHandler | `application/query/{aggregate}/` | `{Criteria}{Aggregate}QueryHandler` | `ListOrdersQueryHandler` |
| Read Model Result | `application/query/{aggregate}/` | `{Aggregate}{Purpose}Result` | `OrderDetailResult`, `OrderSummaryResult` |
| HTTP Request | `interfaces/rest/request/` | `{Action}{Aggregate}Request` | `PlaceOrderRequest`, `AddBookRequest` |
| HTTP Response | `interfaces/rest/response/` | `{Aggregate}{Purpose}Response` | `OrderDetailResponse`, `OrderSummaryResponse` |
| Repository Port (write-side) | `domain/ports/` | `{Aggregate}Persistence` | `OrderPersistence` |
| Read/Search Port (app-side) | `application/port/outbound/` | `{Aggregate}SearchRepository`, `{Aggregate}ReadRepository` | `OrderSearchRepository`, `OrderReadRepository` |
| Cache Port | `application/port/outbound/` | `{Aggregate}Cache` | `BookCache` |
| Service Client Port | `application/port/outbound/` | `{Target}Client` | `CatalogClient` |
| Domain Event (in-process) | `domain/event/` | `{Aggregate}{PastTense}` | `OrderPlaced`, `StockReserved` |
| JPA Entity | `infrastructure/repository/jpa/` | `{Aggregate}JpaEntity` | `OrderJpaEntity` |
| JPA Repository | `infrastructure/repository/jpa/` | `{Aggregate}JpaRepository` | `OrderJpaRepository` |
| ES Document | `infrastructure/repository/elasticsearch/` | `{Aggregate}ElasticDocument` | `OrderElasticDocument` |
| ES Repository | `infrastructure/repository/elasticsearch/` | `{Aggregate}ElasticRepository` | `OrderElasticRepository` |
| REST Controller (write) | `interfaces/rest/` | `{Aggregate}CommandController` | `OrderCommandController` |
| REST Controller (read) | `interfaces/rest/` | `{Aggregate}QueryController` | `OrderQueryController` |
| Outbox Mapper (adapter) | `infrastructure/messaging/outbox/` | `{Service}OutboxMapper` | `OrderOutboxMapper` |
| Kafka Consumer (adapter) | `interfaces/messaging/consumer/` | `{Event}Consumer` | `OrderPlacedConsumer` |
| HTTP Client (adapter) | `infrastructure/client/` | `{Target}RestClient` | `CatalogRestClient` |

---

## Technology Decisions

### Java 21 Features to Use
- **Records** for DTOs, Commands, Queries, Value Objects, Domain Events, Command Results
- **Pattern matching** (`instanceof`, `switch`) in domain logic
- **Virtual Threads** (enabled via `spring.threads.virtual.enabled=true`)
- **Sealed classes** for discriminated unions (e.g., `OrderStatus`)
- **Text blocks** for SQL in tests, JSON fixtures

### Spring Boot Configuration
- Use `@ConfigurationProperties` records for typed config (never raw `@Value` in domain/application layers)
- `@Service` on CommandHandlers/QueryHandlers, `@Component` on Domain Services — standard Spring Boot auto-wiring
- `@Component` on all infrastructure adapters
- **`@Transactional` placement rules:**
  - **Default:** place on the Persistence Adapter's `save()`. Keeps transaction scope minimal; external I/O in the Handler naturally falls outside.
  - **Cross-aggregate (same DB):** if a Handler must save two aggregate roots atomically, place `@Transactional` on the CommandHandler to span both `save()` calls. Document this in an ADR — first verify the aggregate boundary is not drawn incorrectly.
  - **Never on Domain Services:** generates a Spring proxy and creates a hidden infrastructure dependency; Domain Services must remain plain Java.
  - **Never wrap external I/O in a transaction:** HTTP calls and email sends must not occur inside a `@Transactional` boundary. Pre-fetch calls (read before business logic) are naturally outside when `@Transactional` is on the adapter.
  - **Post-commit side effects** — choose the mechanism by the nature of the side effect:
    - Side effect is the **primary purpose** of the command → direct call in Handler after `save()` (transaction already committed at this point)
    - **Reaction** to state change, multiple consumers, or cross-service → Domain Event (`@TransactionalEventListener(phase = AFTER_COMMIT)` for same-service; Outbox for cross-service)
    - **Infrastructure operation** (cache invalidation, search index) → inside Persistence Adapter `save()`, Handler does not need to know
  - **Cross-service atomicity:** impossible at the DB level — use Saga pattern (event-driven steps + compensating transactions).

### CQRS in order
- **Write side**: `CommandBus.dispatch(command)` → `PlaceOrderCommandHandler` / `CancelOrderCommandHandler` → `OrderPersistence` (JPA/PostgreSQL)
- **Read side**: `QueryBus.dispatch(query)` → `GetOrderQueryHandler` / `ListOrdersQueryHandler` → `OrderSearchRepository` (ElasticSearch), bypassing the domain layer entirely. ES fallback uses `OrderReadRepository` (JPA projection returning DTO directly — never reconstitutes domain entities on the read path).
- `SpringCommandBus` / `SpringQueryBus` in `infrastructure/bus/` auto-discover handlers, log dispatch, and measure duration
- Domain events trigger read-model projections via `OrderEventConsumer` (Kafka consumer inside `order`)

### Domain Events & Inter-Service Communication
- Domain events are published to Kafka after successful aggregate state change via the Outbox pattern
- `shared-events` module contains **only** Avro schema files — no executable business logic
- Services never call each other's domain layer directly — only via events or REST APIs (ports)
- Use the **Outbox Pattern** to guarantee at-least-once event delivery (write event to `outbox` table in same transaction, then relay via Kafka)
- `outbox.relay.strategy` in each service's `application.yml` defaults to `scheduler`. Debezium CDC is no longer used; the scheduler-based relay covers all environments.
- Cross-service stock operations (e.g., releasing reserved stock on order cancel) are event-driven: `OrderCancelled` integration event carries item details; catalog service consumes it and releases stock idempotently. No synchronous HTTP calls for cross-service state changes.

### Database
- Each service owns its PostgreSQL database exclusively (no shared tables)
- Use Flyway for schema migrations (`src/main/resources/db/migration/`)
- seedwork base tables (outbox, processed_events, consumer_retry_events) are provided as SQL scripts in `classpath:db/seedwork/`; each service must add `classpath:db/seedwork` to `spring.flyway.locations`
- JPA entities in `infrastructure/repository/jpa/` — never expose them outside that package
- Map JPA entities ↔ domain objects explicitly in the persistence adapter
- Use `@Version` on JPA entities for aggregates that can have concurrent state transitions (optimistic locking)

---

## Testing Strategy

```
Unit tests      → Domain layer + Application layer (no Spring context, no Docker)
Integration     → Each adapter in isolation (Testcontainers: Postgres, Redis, Kafka, ES)
Component       → Full service in-memory (SpringBootTest + Testcontainers)
Contract        → REST API Bi-Directional Contract Testing via PactFlow
E2e             → REST calls against a pre-deployed test environment; no infra setup in CI
```

- Domain tests: plain JUnit 5, no Mockito for domain objects — test via behavior
- Use case tests: mock out ports using Mockito or hand-rolled test doubles
- Persistence adapter tests: `@DataJpaTest` + Testcontainers PostgreSQL
- Controller tests: `@WebMvcTest` + MockMvc (mock use cases)

### Contract Testing (Bi-Directional, PactFlow)

Use **PactFlow** (托管服务，Starter 免费套餐，无需自托管) for REST API contract testing between services.

**Scope:** `order-service` (Consumer) → `catalog-service` (Provider) via `CatalogRestClient`

**How it works (BDCT):**
- Consumer (`order`) writes Pact tests → generates pact file → publishes to PactFlow
- Provider (`catalog`) generates OpenAPI spec (`springdoc-openapi`) → uploads to PactFlow
- PactFlow cross-validates consumer pact against provider spec automatically — no provider-side replay tests needed

**SwaggerHub** is the API registry where the generated OAS is published for human consumption and governance. PactFlow (SmartBear, same vendor) is the BDCT engine.

**Test location:**
```
order/src/test/java/.../contract/
└── CatalogClientPactTest.java     ← Consumer test (@Tag("contract"), no Spring context)
                                      generates build/pacts/order-service-catalog-service.json
```

**No `CatalogProviderPactTest.java`** — in BDCT, the provider side is a CI step, not a test class:
```bash
# CI fetches live spec and publishes to PactFlow as provider contract
curl "${CATALOG_BASE_URL}/v3/api-docs" -o catalog-oas.json
pactflow publish-provider-contract catalog-oas.json --provider catalog-service ...
```

**Run consumer contract tests locally** (no Testcontainers required):
```bash
cd order && ./gradlew test --tests "com.example.order.contract.*"
```

**CI workflows:**
- `.github/workflows/contract-test.yml` — 3 jobs: consumer → provider → can-i-deploy gate
- `.github/workflows/e2e.yml` — separate; runs after contract gate passes

**Kafka schema compatibility:** handled by Confluent Schema Registry (Avro) — not Pact's responsibility.

**Required secrets** (GitHub → Settings → Secrets):
```
PACTFLOW_BASE_URL      https://{org}.pactflow.io
PACTFLOW_API_TOKEN     {pactflow-api-token}
SWAGGERHUB_API_KEY     {swaggerhub-api-key}
SWAGGERHUB_OWNER       {swaggerhub-org-name}
```

---

## Observability

Every service must:
1. Export OpenTelemetry traces via `opentelemetry-spring-boot-starter`
2. Propagate `traceparent` header across HTTP and Kafka messages
3. Use `io.micrometer:micrometer-registry-prometheus` for metrics
4. Log structured JSON using Logback with trace/span ID fields

Span naming convention: `{service}.{aggregate}.{operation}` (e.g., `order.order.place`)

---

## Build & Tooling

### Gradle
- Each service (`catalog`, `order`, `notification`, `seedwork`, `shared-events`, `e2e`) is an **independent Gradle project** with its own `settings.gradle.kts` — there is no root multi-project build.
- Run from each service directory: `cd {service} && ./gradlew bootRun` / `./gradlew test`
- Dependency build order: `seedwork publishToMavenLocal` → `shared-events publishToMavenLocal` → service builds

### Docker / Jib
- Services use **Jib** (no Dockerfile) to build OCI images directly from compiled classes.
- Build to local Docker daemon: `cd {service} && ./gradlew jibDockerBuild`
- Push to registry: `cd {service} && ./gradlew jib`
- Image names are defined in each service's `build.gradle.kts` under the `jib { to { image = ... } }` block.
- Base image: `eclipse-temurin:21-jre-alpine`

### Helm
- Each service has its own Helm chart at `{service}/helm/`
- `shared-events/helm/` — registers Avro schemas in Schema Registry and Kafka topics; deploys to `infra` namespace as a post-install Job; must run after infra and before any microservice
- Infrastructure umbrella chart at `infrastructure/helm/` (Chart.yaml at that root) composes all middleware components (Postgres, Redis, Kafka, ES, etc.)
- Config per environment: `values-local.yaml`, `values-staging.yaml`, `values-prod.yaml`

---

## What NOT To Do

### Architecture
- Do NOT put write-side Repository interfaces in `application/port/outbound/` — they belong in `domain/ports/` (parameters representing domain objects must use domain types, not raw `UUID`/`String`; primitive types for pagination or simple filters are fine)
- Do NOT import application-layer types in `domain/ports/` — Repository interfaces must only reference domain model types (`OrderId`, `Order`, etc.)
- Do NOT place REST controllers or Kafka consumers inside `infrastructure/` — they are primary adapters and belong in `interfaces/`
- Do NOT inject concrete handler classes into controllers or Kafka consumers — always dispatch via `CommandBus` / `QueryBus`
- Do NOT add cross-cutting logic (logging, metrics) to individual handlers — put it in the bus implementation
- Do NOT define per-use-case inbound port interfaces (`PlaceOrderUseCase` etc.) — `CommandBus` / `QueryBus` serve as the inbound ports

### Domain Model
- Do NOT use behaviorally invasive annotations in `domain/` — no `@Entity`, `@Transactional`, JPA relationship annotations. `@Component`/`@Service` on Domain Services is acceptable.
- Do NOT import JPA/Redis/Kafka in `domain/` packages — zero infrastructure dependencies
- Do NOT add repositories or ports as constructor parameters to Domain Services — Domain Services are pure functions; all data is passed in by the Handler

### Application Layer
- Do NOT import JPA/Redis/Kafka in `application/` packages — only `@Service`, Lombok, and domain/seedwork imports
- Do NOT have Command Handlers return Query-layer Results (`{Aggregate}DetailResult`, etc.) — use dedicated `{Action}{Aggregate}Result` records assembled from in-memory domain state
- Do NOT put business logic (if/else on business conditions) in Command Handlers — delegate to the Domain Model or Domain Service; the Handler only orchestrates
- Do NOT reconstitute domain entities on the read path — Query Handlers return DTOs directly from DB/ES projections, bypassing the domain layer entirely

### Transactions & I/O
- Do NOT add `@Transactional` to Domain Services — creates a hidden infrastructure dependency via Spring proxy
- Do NOT wrap external I/O (HTTP calls, email sends) inside a `@Transactional` boundary — holds DB connection during network I/O; rollback cannot undo sent emails
- Do NOT release cross-service state (e.g., catalog stock) via synchronous HTTP after a DB commit — use the event-driven approach: publish a domain event, let the other service consume it

### Data & Events
- Do NOT call `EventDispatcher` or any Kafka publisher from a CommandHandler — publishing is a side-effect of persistence via `OutboxWriteListener`
- Do NOT skip the mapper layer — never return JPA entities from the persistence adapter to the application layer
- Do NOT create shared domain objects across microservices — duplicate simple types (e.g., `Money`) per bounded context
- Do NOT put cache management (put/invalidate) in Command Handlers — it is an infrastructure concern that belongs in the Persistence Adapter's `save()`

---

## Common Commands

```bash
# Publish shared libraries to mavenLocal (required before building services)
cd seedwork      && ./gradlew publishToMavenLocal
cd shared-events && ./gradlew publishToMavenLocal

# Run tests for a single service
cd order && ./gradlew test

# Build a service image to local Docker daemon (Jib — no Dockerfile needed)
cd catalog && ./gradlew jibDockerBuild

# Run e2e tests against a live environment (set URLs via env vars or GitHub secrets)
cd e2e && CATALOG_BASE_URL=http://... ORDER_BASE_URL=http://... NOTIFICATION_BASE_URL=http://... ./gradlew test

# Deploy infrastructure middleware to local k8s
helm upgrade --install bookstore-infra ./infrastructure/helm -f infrastructure/helm/values.yaml

# Prepare Kafka resources (pipeline step — run before deploying shared-events chart)
# 1. Create ConfigMap from .avsc source files (schema data, not managed by Helm)
kubectl create configmap avro-schemas -n infra \
  --from-file=shared-events/src/main/avro/com/example/events/v1/ \
  --dry-run=client -o yaml | kubectl apply -f -
# 2. Deploy shared-events chart: post-install Jobs create topics + register schemas
helm upgrade --install shared-events ./shared-events/helm -n infra

# Deploy a service to local k8s
helm upgrade --install catalog ./catalog/helm -f catalog/helm/values.yaml
```

---

## Adding a New Feature (Checklist)

1. [ ] Define the domain model change in `domain/model/`
2. [ ] Add/update domain event in `domain/event/`
3. [ ] If the feature needs persistence: define or update the write-side port in `domain/ports/` (parameters that are domain objects use domain types, not raw `UUID`/`String`)
4. [ ] If the feature needs external clients, cache, or search: define ports in `application/port/outbound/`
5. [ ] Add `Command<Result>` record + `{Action}{Aggregate}Result` record + `@Service CommandHandler` in `application/command/{aggregate}/`
6. [ ] Add `Query<R>` record + `@Service QueryHandler` + `{Aggregate}{Purpose}Result` in `application/query/{aggregate}/`
7. [ ] Implement persistence adapter in `infrastructure/repository/jpa/` — include cache invalidation here if needed
8. [ ] Add REST endpoint in `interfaces/rest/` — dispatch via `CommandBus` / `QueryBus`; map `*Result` → `*Response` (HTTP) in controller
9. [ ] Add Flyway migration if schema changes
10. [ ] Write unit tests for domain behavior (no mocks, no Spring context)
11. [ ] Write unit tests for handler (mock ports with Mockito)
12. [ ] Write integration test for persistence adapter (Testcontainers)
13. [ ] Update OpenAPI spec in `docs/api/`
