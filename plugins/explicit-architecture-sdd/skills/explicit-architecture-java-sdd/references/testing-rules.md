# Testing Rules

Use these rules with Java 17, Spring Boot 3, JUnit 5, AssertJ, Mockito for unit tests, and disposable real/near-real dependencies for integration tests. Keep this file as decision guidance; load full test code from `assets/templates/tests/` only when writing the corresponding test.

## Fowler Test Types Mapped to Explicit Architecture

| Test type | Boundary | What to verify | Quantity |
|---|---|---|---|
| Unit | One class or in-memory cluster | Domain rules, handler orchestration, DTO/event translation | Most |
| Integration | One adapter plus external protocol/component | Technology boundary assumptions | Few, high value |
| Component | One service/component through public entry | Service behavior with dependencies stubbed/contained | Key use cases |
| Contract | Cross-boundary API/message/schema | Consumer expectations and provider compatibility | Every boundary |
| End-to-End | Full deployed business journey | System-level collaboration and deployment wiring | Very few |

## Test Template Routing

When generating tests, read only the needed template:

| Need | Template |
|---|---|
| Aggregate/value object/domain service unit test | `assets/templates/tests/domain-unit-test.java` |
| Command handler unit test | `assets/templates/tests/handler-unit-test.java` |
| Query handler unit test | `assets/templates/tests/query-handler-unit-test.java` |
| REST controller slice test | `assets/templates/tests/controller-slice-test.java` |
| Persistence adapter integration test with Testcontainers | `assets/templates/tests/persistence-adapter-testcontainers-test.java` |
| Message broker integration test with Testcontainers | `assets/templates/tests/message-broker-testcontainers-test.java` |
| Message broker embedded fallback test | `assets/templates/tests/message-broker-embedded-test.java` |
| Cache adapter integration test | `assets/templates/tests/cache-adapter-testcontainers-test.java` |
| Search/read-model adapter integration test | `assets/templates/tests/search-adapter-testcontainers-test.java` |
| External HTTP client integration test | `assets/templates/tests/http-client-wiremock-test.java` |
| File storage adapter integration test | `assets/templates/tests/file-storage-tempdir-test.java` |
| Email/SMS/payment/provider client test | `assets/templates/tests/provider-client-wiremock-test.java` |
| Raw WebSocket receive integration test | `assets/templates/tests/websocket-receive-integration-test.java` |
| STOMP-over-WebSocket receive integration test | `assets/templates/tests/stomp-receive-integration-test.java` |

Templates use `{{PLACEHOLDER}}` markers. Replace every marker with project-specific names and delete comments that are not useful in the final test.

## Unit Test Rules

Domain unit tests:

- No Spring context.
- No database, network, message system, cache, or filesystem dependency.
- No Mockito for domain collaborators unless unavoidable.
- Test behavior and invariants, not private implementation.

Handler unit tests:

- No Spring context.
- Mock or fake ports only.
- Do not retest domain internals.
- Verify load -> domain call -> save -> result behavior.
- Query handlers verify port selection, fallback, empty result, and authorization/scope if present.

Interface unit/slice tests:

- Mock the application entrypoint.
- Verify route/method/status, DTO mapping, validation, and error response.
- Do not verify persistence or domain behavior.

## Adapter Integration Test Rules

- Test the adapter and the real or protocol-equivalent external boundary.
- Prefer disposable real or near-real dependencies, such as Testcontainers-managed services.
- Run real schema migrations when testing persistence adapters whenever the project uses migrations.
- Use protocol-level stubs for remote third-party APIs when calling the real sandbox is slow, costly, flaky, or unsafe.
- Use embedded components only as a fallback when their behavior is known to match the real dependency closely enough for the specific boundary under test.
- Do not use Mockito mocks as the primary boundary in adapter integration tests; mocks belong in handler/unit tests.
- Keep scenarios few: happy path, mapping, important errors, transaction/consistency behavior.
- Do not run full business journeys here.

Recommended environment choices:

| Adapter boundary | Preferred integration environment | Fallback |
|---|---|---|
| Persistence adapter | Testcontainers real database + real migrations | Embedded database only if dialect/transaction behavior is equivalent enough |
| Message producer/consumer | Testcontainers real broker | Embedded broker only for lightweight smoke tests |
| Cache adapter | Testcontainers real cache service | In-memory fake only for component tests, not adapter integration tests |
| Search/read-model adapter | Testcontainers real search/read-model service | Repository-level fake for handler tests only |
| External HTTP client | Protocol-level stub such as WireMock or MockWebServer; sandbox API for critical contracts | Mockito only in handler tests |
| File/object storage adapter | Testcontainers service, sandbox service, or temporary filesystem depending on protocol complexity | Temporary filesystem for local file adapters |
| Email/SMS/payment adapter | Sandbox API or protocol-level stub | Fake sender for component tests |
| WebSocket adapter | `@SpringBootTest(RANDOM_PORT)` + real WebSocket/STOMP client | Handler unit test only for pure message-routing logic |

## Component Test Rules

- Drive through public interfaces.
- Stub or contain dependencies outside the component boundary.
- Cover critical write/read happy paths and a few important errors.
- Do not cover every business branch; lower-level tests own that.
- Use bounded polling for async behavior. Do not use arbitrary sleep.

## Contract Test Rules

Use for:

- HTTP/RPC APIs.
- Message schemas.
- File/batch interfaces.
- Public SDKs.

Rules:

- Capture consumer expectations.
- Keep provider compatibility automated in CI when possible.
- Prefer additive compatible changes.
- Use parallel change for breaking migrations.

## End-to-End Test Rules

Use only for:

- Core user journeys crossing multiple components.
- Real deployment/routing/auth integration.
- Eventual consistency chains.
- Regression after service split/merge or communication architecture change.

Rules:

- Keep small.
- Use independent test data.
- Use bounded polling for eventual consistency.
- Assert diagnostic states at each major boundary.

## Change-to-Test Matrix

| Change | Required tests |
|---|---|
| New domain rule | Domain unit; related handler unit |
| New command use case | Domain unit, command handler unit, interface test, component happy path |
| New query use case | Query handler unit, interface test, read adapter integration |
| New persistence shape | Persistence adapter integration, migration verification, related component test |
| New external client | Client integration, handler unit, contract test if cross-boundary |
| New message event | Event mapper unit, schema compatibility, consumer unit, component test |
| New cache/search/file behavior | Adapter integration, handler/query unit, component test |
| New WebSocket receive path | WebSocket integration test with real client; handler unit test for routing logic |
| New multi-component journey | Lower-level tests plus one e2e happy path |

## Completion Gate

Before claiming done:

- Domain/application tests pass.
- Relevant adapter tests pass or are explicitly impossible with reason.
- Component/contract/e2e tests are added only where their boundary is needed.
- No test relies on arbitrary sleep for async behavior.
- Every failure points to a clear architectural boundary.
