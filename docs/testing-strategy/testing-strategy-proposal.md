# Proposal for Adopting Automated Testing Standards in Microservices

**Document type:** Internal technical proposal
**Audience:** All engineering teams
**Reference:** Toby Clemson / ThoughtWorks, "Testing Strategies in a Microservice Architecture"

---

## I. Current State and Problems

Test coverage varies significantly across services. The following issues are widespread:

| Symptom | Impact |
|---|---|
| Many services have no automated tests at all | Every release depends on manual regression; low efficiency, high risk of missed defects |
| Tests exist but lack standards; test boundaries are unclear | Tests are hard to maintain, frequently break with code changes, and are gradually ignored |
| Test granularity is either too coarse (E2E only) or too fine (only a handful of unit tests) | Coverage gaps, slow defect localisation |
| Each team figures things out independently with no shared strategy | Knowledge cannot be reused; high onboarding cost for new members |

These problems could be patched manually in a monolithic era, but the cost escalates sharply under a microservice architecture: **each service is deployed and evolved independently — any change without systematic test coverage is walking a tightrope.**

---

## II. The Real Cost of Not Writing Tests

> "We don't have time to write tests." — The subtext is: "We do have time to manually test over and over, chase production bugs, and perform emergency rollbacks at 3 a.m."

**Short term:**
- No safety net when requirements change; one modification breaks everything downstream
- QA or developers spend significant time on manual regression before each release
- Bugs are discovered only in production; fixing them costs 10x more than catching them during development

**Medium term:**
- Code becomes increasingly hard to change; technical debt accumulates; refactoring becomes high-risk
- Teams lose confidence in the system; workarounds replace core fixes, and nobody dares touch critical logic
- Onboarding is difficult; without tests as "living documentation", understanding system behaviour is a black art

**Long term:**
- System stability degrades; SLAs become hard to guarantee
- Release frequency is forced down; competitive response time slows
- When key engineers leave, the system becomes a black box that nobody will touch

---

## III. Recommended Testing Strategy: The Layered Test Pyramid

Drawing on the strategy distilled by ThoughtWorks from large-scale microservice practice, we recommend building a five-layer testing model:

```
         ▲
        /  \
       / E2E \          ← Few tests; verify critical business workflows
      /────────\
     / Contract \       ← At service boundaries; prevent breaking interface changes
    /────────────\
   /  Component   \     ← Per-service level; isolate external dependencies
  /────────────────\
 /   Integration    \   ← Adapter level; verify connectivity with DB/cache/message queues
/────────────────────\
         Unit          ← Most tests; verify domain logic and coordination logic
```

### Layer Descriptions and Implementation

#### 1. Unit Tests
**Goal:** Verify the behaviour of the smallest testable unit; millisecond-level execution; no external dependencies.

| Category | Use case | Approach |
|---|---|---|
| **Sociable** | Domain layer: complex calculations, state transitions | Use real domain objects; no mocking |
| **Solitary** | Coordination layer (Service Layer, Resources, Repositories): delegation and mapping logic | Isolate dependencies with Mockito or equivalent |

**Tools:** JUnit 5 + Mockito
**Standard:** Core domain logic coverage no less than 80%; all new business logic must be accompanied by unit tests

---

#### 2. Integration Tests
**Goal:** Verify communication paths between the service and external components (database, Redis, Kafka); surface incorrect interface assumptions.

**Approach:** Write a small number of tests per adapter type (Repository, Gateway, HTTP Client), using Testcontainers to start real dependencies.

**Tools:** JUnit 5 + Testcontainers + `@DataJpaTest`
**Standard:** At least 1–3 integration tests per external adapter type, covering the happy path and key error paths

> **Note:** Keep the number of integration tests modest. They depend on external components, run slowly, and have a higher intermittent failure rate. Test only the communication boundary; do not duplicate logic already covered by unit tests.

---

#### 3. Component Tests
**Goal:** Treat the entire microservice as a black box; verify complete business requirements in isolation from external dependencies.

**Approach:**
- **In-process (preferred):** `SpringBootTest` + `MockMvc`; stub external services with WireMock; use Testcontainers for the database
- **Out-of-process (when necessary):** Start the real JAR + external stub server

**Tools:** SpringBootTest + MockMvc + WireMock + Testcontainers
**Standard:** Cover every core business use case (happy path + main error scenarios)

---

#### 4. Contract Tests
**Goal:** Ensure service-to-service interfaces do not change in a breaking way, without deploying the full system.

**Approach:**
- The **consumer** writes the contract (expected request/response structure)
- The contract test suite runs in the **provider's CI pipeline**
- Any provider change that breaks a consumer contract causes an immediate build failure

**Tools:** Pact or Spring Cloud Contract
**Scope:** Prioritise adoption on core services with complex call chains and multiple consumers
**Standard:** All cross-team service calls must be guarded by contract tests

---

#### 5. End-to-End Tests
**Goal:** Verify complete business workflows through public interfaces; the last line of defence.

**Approach:** Write a small number of scenarios for critical user journeys, using an isolated test environment (created on demand via Docker Compose or Helm)

**Tools:** REST-assured + Docker Compose
**Standard:** Cover only the 3–5 most critical business flows; do not aim for high coverage

---

## IV. Rollout Roadmap

We recommend a three-phase rollout to avoid the resistance that comes with large-scale, all-at-once change:

### Phase 1: Establish a Baseline (1–2 months)
**Goal:** Ensure every service has at least a basic level of testing; build awareness of standards.

- [ ] Draft and publish the Testing Standards document (a simplified version of this document with code examples)
- [ ] Add test steps to CI pipelines; failed tests block merges
- [ ] Backfill unit tests for the core domain logic of existing services
- [ ] Designate a Testing Champion per team to drive adoption and answer questions

### Phase 2: Solidify the Foundation (2–4 months)
**Goal:** All new code is fully test-covered; existing code is incrementally backfilled.

- [ ] New feature development: unit tests + component test happy path are part of the Definition of Done (DoD)
- [ ] Each service must have at least 1 component test covering its most critical business flow
- [ ] Add integration tests for DB/Redis/Kafka adapters
- [ ] Set up a test coverage monitoring dashboard and establish baseline targets per team

### Phase 3: Deepen and Elevate (4–6 months)
**Goal:** Full rollout of contract testing; eliminate interface risks between services.

- [ ] Introduce Pact / Spring Cloud Contract between core services
- [ ] E2E tests cover critical user journeys
- [ ] Include test quality in technical debt reviews, linked to iteration planning

---

## V. Common Concerns and Responses

**Q: Writing tests takes too much time and slows down feature delivery.**
A: There is an upfront investment. But once a project enters its maintenance phase, the cost of having no tests far exceeds that investment. Industry data shows that fixing a bug found during development costs **1/10** of fixing the same bug in production. Furthermore, good tests make subsequent feature development faster, since refactoring and changes no longer require a full manual regression.

**Q: Our business logic changes frequently; tests will keep failing and be expensive to maintain.**
A: Tests that break frequently usually indicate they are written at the wrong layer (too many E2E tests, too few unit tests). With a pyramid structure, lower-layer tests (Unit) are insensitive to changes in higher layers; only genuine interface or behaviour changes should cause test failures.

**Q: Setting up the test environment is too complex.**
A: This is a technical problem that can be solved. Testcontainers makes external dependencies for integration tests spin up with a single command; WireMock can make external service dependencies disappear entirely. We can provide standard templates to reduce the initial cost.

**Q: Our existing code has no tests; retrofitting now is too hard.**
A: You don't need to do it all at once. The principle is: **touch it, test it**. Each time you fix a bug or add a new feature, add tests for the code you change. Coverage will improve naturally over a few months without disrupting delivery.

---

## VI. Closing Remarks

Automated testing is not an optional extra — it is **foundational infrastructure for engineering quality in a microservice architecture**.

A microservice system without systematic testing is like a racing car with no seatbelts and no ABS — it might go fast most of the time, but when something goes wrong, the cost is extreme.

We do not need 100% coverage, and we do not need to get there overnight. We simply need to reach the point where:
> **Every new line of code has test coverage; every release comes with the confidence to say "I verified this."**

We invite all team leads and engineers to drive this initiative forward together.
If you have questions or need support, please reach out to discuss specific approaches.

---

*Appendices:*
*- [Testing Strategies in Detail (Chinese)](./testing-strategies-zh-standalone.md)*
*- [Testing Strategies in Detail (English original)](./testing-strategies-standalone.md)*
