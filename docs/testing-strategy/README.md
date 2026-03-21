# Testing Strategies in a Microservice Architecture — Overview

> This document is compiled from Toby Clemson's classic Infodeck published on the Martin Fowler website. It covers all 25 slides, including the original screenshots and key takeaways from each section.

---

## I. Introduction and Agenda (Slides 1–2)

Microservice architecture brings the convenience of independent deployment and scaling, but it also makes testing more complex. Network partitions require us to re-examine the testing strategies used for monolithic applications.

![Slide 1: Title](./images/slide_01_1773300966630.png)

![Slide 2: Agenda](./images/slide_02_1773300972889.png)

**Key points:**
- Microservices enable parallel development across teams.
- Tests must adapt to a new environment defined by network isolation and distributed collaboration.

---

## II. What Microservices Are and Their Internal Anatomy (Slides 3–5)

A microservice is a collection of collaborating services, typically integrated via REST over HTTP. Understanding their internal components is essential for designing an effective testing approach.

![Slide 3: Anatomy](./images/slide_03_1773301034248.png)

![Slide 4: Module Breakdown](./images/slide_04_v2_1773301040439.png)

![Slide 5: Connections](./images/slide_05_final_1773301090097.png)

**Module breakdown:**
- **Resources**: Handle protocol mapping (e.g., HTTP to Domain).
- **Service/Domain**: Carry the core business logic.
- **Gateways**: Encapsulate messaging with external systems.
- **Repositories**: Interact with the persistence layer.

---

## III. Unit Testing (Slides 6–9)

Unit tests verify logic at the class level. In microservices, we need to choose between "sociable" and "solitary" styles.

![Slide 6: Context](./images/slide_06_1773301189076.png)

![Slide 7: Intro](./images/slide_07_1773301206003.png)

![Slide 8: Styles](./images/slide_08_1773301212270.png)

![Slide 9: Limits](./images/slide_09_1773301269517.png)

**Recommendations:**
- **Sociable**: Suited for complex domain logic; allows interaction with real collaborators.
- **Solitary**: Suited for coordination logic; uses Test Doubles to isolate dependencies.
- **Limitation**: Unit tests cannot guarantee correctness of cross-module interactions — higher-level tests are required.

---

## IV. Integration Testing (Slides 10–11)

Verify communication paths and interface contracts, especially when interacting with databases and other services.

![Slide 10: Definition](./images/slide_10_1773301283553.png)

![Slide 11: Feedback](./images/slide_11_1773301385103.png)

**Key notes:**
- Focus on the behavior of integrated modules.
- These tests can be slow and flaky due to reliance on external components.
- Write only a small, curated set of integration tests.

---

## V. Component Testing (Slides 12–16)

Test the entire microservice as a standalone component, typically either in-process or via external stubs.

![Slide 12: Need](./images/slide_12_1773301401016.png)

![Slide 13: Definition](./images/slide_13_1773301449981.png)

![Slide 14: In-process](./images/slide_14_1773301471020.png)

![Slide 15: Resources](./images/slide_15_1773301546751.png)

![Slide 16: Out-of-process](./images/slide_16_1773301741483.png)

**Testing approaches:**
- **In-process**: Uses an in-memory database and in-memory stubs; fast to execute.
- **Out-of-process**: Deploys the real artifact and uses external stub servers (e.g., moco, mountebank); closer to a production environment.

---

## VI. Contract Testing (Slides 17–19)

Ensure that service providers fulfil consumer expectations without deploying the entire system.

![Slide 17: Combination](./images/slide_17_1773301763279.png)

![Slide 18: Intro](./images/slide_18_1773301772636.png)

![Slide 19: Sum](./images/slide_19_1773301806553.png)

**Core idea:**
- Consumers define their expectations of the Provider (request/response structure).
- The Provider continuously verifies these contracts during its build to ensure no breaking changes are published.

---

## VII. End-to-End Testing (Slides 20–22)

Verify entire business workflows through public interfaces (e.g., GUI or REST API).

![Slide 20: Definition](./images/slide_20_1773301882960.png)

![Slide 21: Boundary](./images/slide_21_1773302095963.png)

![Slide 22: Tips](./images/slide_22_final_1773302152612.png)

**Golden rules:**
- Write as few E2E tests as possible.
- Focus on critical user journeys (personas).
- Keep test data independent to avoid interference between test runs.

---

## VIII. Summary and the Test Pyramid (Slides 23–25)

Microservice architecture offers more testing options; the key is maintaining the right balance.

![Slide 23: Options](./images/slide_23_1773302188592.png)

![Slide 24: Pyramid](./images/slide_24_final_1773302284085.png)

![Slide 25: Summary](./images/slide_25_1773302301081.png)

**Final recommendations:**
- Build a solid foundation of unit tests.
- Use integration, component, and contract tests to manage inter-service complexity.
- Use a small number of E2E tests as the last line of defence.
