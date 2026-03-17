# ADR-011: API Governance with SwaggerHub + PactFlow Bi-Directional Contract Testing

- **Status**: Accepted
- **Date**: 2026-03-13
- **Deciders**: Architecture Team

---

## Context

We need a solution for two related but distinct concerns:

1. **API Registry**: A single source of truth for published REST API specifications, enabling consumers to discover and understand provider contracts before writing code.
2. **Contract Testing**: Automated verification that the provider's API implementation matches what consumers depend on, catching breaking changes before they reach production.

### Approaches Evaluated

| Approach | API Registry | Contract Testing |
|---|---|---|
| A — No tooling | Each team reads source code | Manual, ad-hoc |
| B — OpenAPI in Git | Spec files checked into repo | Schemathesis against live service |
| C — SwaggerHub standalone | SwaggerHub hosts specs | No contract-level verification |
| D — Pact CDC (traditional) | None | Provider replays consumer pacts against actual code |
| **E — SwaggerHub + PactFlow BDCT** | **SwaggerHub hosts specs** | **PactFlow cross-validates OAS vs Pact; no provider replay** |

### Why BDCT Instead of Traditional Pact CDC

Traditional Pact CDC requires the **provider team to run consumer pacts against their codebase**, creating organisational coupling: provider CI depends on consumer pact state. This breaks down when:

- Provider and consumer are owned by different teams on different release cadences
- The provider has complex setup requirements (DB migrations, Kafka, etc.)
- A consumer adds a pact test and the provider team must respond before either can ship

**Bi-Directional Contract Testing** decouples the two sides:

- Consumer publishes a pact (what they call and what response shape they need)
- Provider publishes an OAS spec (what they actually implement)
- PactFlow compares the two — no test execution on the provider side required

---

## Decision

Use **SwaggerHub** as the API registry and **PactFlow** (SmartBear) for BDCT across all REST service boundaries.

### Scope

The `order-service` → `catalog-service` REST boundary is the only synchronous HTTP dependency:

| Consumer | Provider | Interactions |
|---|---|---|
| order-service | catalog-service | `GET /api/v1/books/{id}/stock`, `POST /api/v1/books/{id}/stock/reserve`, `POST /api/v1/books/{id}/stock/release` |

Notification service has no synchronous HTTP dependencies. Async Kafka events are covered by Avro Schema Registry (ADR-003), not by Pact.

---

## Implementation

### Component Responsibilities

```
order-service (Consumer)                  catalog-service (Provider)
─────────────────────────────────         ──────────────────────────────────
CatalogClientPactTest.java                springdoc-openapi starter
  └─ defines 3 interactions                 └─ exposes /v3/api-docs at runtime
  └─ runs against Pact mock server
  └─ generates build/pacts/*.json

CI: pact-broker publish                   CI: curl /v3/api-docs → catalog-oas.json
      └─► PactFlow                              └─► PactFlow (provider contract)
                                                └─► SwaggerHub (API registry)
                        ▼
              PactFlow cross-validates
              consumer pact vs provider OAS
                        ▼
                 can-i-deploy gate
```

### springdoc-openapi (catalog)

Added `springdoc-openapi-starter-webmvc-ui` to catalog. Exposes:
- `GET /v3/api-docs` — machine-readable OpenAPI JSON (consumed by CI and PactFlow)
- `GET /swagger-ui.html` — human-readable interactive UI

No additional configuration needed — springdoc auto-discovers `@RestController` annotations.

### Consumer Pact Test (`order/src/test/java/.../contract/CatalogClientPactTest.java`)

- Annotated `@Tag("contract")` for targeted CI execution (no Testcontainers required)
- Instantiates `CatalogRestClient` directly with the Pact mock server URL (no Spring context)
- Three interactions: `checkStock`, `reserveStock`, `releaseStock`
- Output: `order/build/pacts/order-service-catalog-service.json`

The consumer pact specifies only what order-service **uses** from the response, not the full provider response shape. This is the key BDCT principle: minimal coupling.

### Provider Contract (CI step, not a test class)

No `CatalogProviderPactTest.java` exists — this is intentional in BDCT. The CI fetches the live spec:

```bash
curl "${CATALOG_BASE_URL}/v3/api-docs" -o catalog-oas.json
pactflow publish-provider-contract catalog-oas.json \
  --provider catalog-service --provider-app-version $VERSION --verification-success
```

### CI Workflow (`.github/workflows/contract-test.yml`)

Three jobs:

| Job | Steps |
|---|---|
| `consumer` | Run `@Tag("contract")` tests → publish pact to PactFlow |
| `provider` | Fetch `/v3/api-docs` from live test env → publish to PactFlow + SwaggerHub |
| `can-i-deploy` | `pact-broker can-i-deploy` for both services; blocks on incompatibility |

All Pact CLI operations use `pactfoundation/pact-cli` Docker image — no gem installation required.

### Required GitHub Secrets

| Secret | Description |
|---|---|
| `PACTFLOW_BASE_URL` | PactFlow instance URL (e.g. `https://yourorg.pactflow.io`) |
| `PACTFLOW_API_TOKEN` | PactFlow read/write API token |
| `SWAGGERHUB_API_KEY` | SwaggerHub API key for spec upload |
| `SWAGGERHUB_OWNER` | SwaggerHub organization/owner name |
| `E2E_CATALOG_BASE_URL` | Catalog service base URL in test environment (also used by e2e.yml) |

---

## Consequences

### Positive

- **No provider test coupling**: catalog team is never blocked by order's pact state; each service ships independently
- **Single OAS source of truth**: springdoc generates the spec from actual controller code — no hand-maintained YAML files that drift from implementation
- **SwaggerHub as API portal**: consumers can browse `/swagger-ui.html` before writing integration code; spec is versioned per Git SHA
- **Breaking change detection**: PactFlow's `can-i-deploy` gate fails the PR if a catalog change breaks any consumer interaction — surfaced in CI, not in production
- **No extra test infrastructure**: consumer tests run without Testcontainers (fast, cheap in CI)

### Negative

- **Live test env dependency**: the provider job fetches the spec from the running test environment. If the test env is down, the provider CI step fails
- **Two SmartBear accounts**: SwaggerHub and PactFlow are separate accounts (though same vendor). The free tier of PactFlow limits teams; check quotas before scaling
- **BDCT limitation**: PactFlow validates that the OAS *allows* the consumer interaction, but does not run the actual catalog code. A bug in the implementation that matches the OAS schema will not be caught by BDCT — integration tests (ADR-001 test pyramid) cover this
- **Docker-in-CI**: the `pactfoundation/pact-cli` image requires Docker available on the runner (available by default on `ubuntu-latest`)

### What This Does Not Replace

| Concern | How it's handled |
|---|---|
| Kafka event schema compatibility | Avro Schema Registry (ADR-003) |
| Catalog service internal correctness | Unit + integration tests (Testcontainers) |
| Full stack verification | E2E tests (`.github/workflows/e2e.yml`) |
| mTLS + traffic policies between services | Istio (ADR-004) |
