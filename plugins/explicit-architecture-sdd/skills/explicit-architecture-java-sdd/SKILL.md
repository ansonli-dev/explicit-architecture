---
name: explicit-architecture-java-sdd
description: Use when planning, implementing, reviewing, or testing Java 17 + Spring Boot 3 code in projects using Explicit Architecture, DDD, Hexagonal Architecture, Onion/Clean Architecture, CQRS, Command/Query handlers, ports and adapters, domain events, or Fowler-style microservice testing. Trigger during Superpowers SDD, writing-plans, TDD, subagent implementation, and code review when code must follow strict domain/application/infrastructure/interfaces boundaries.
---

# Explicit Architecture Java SDD

Use this skill to keep Java 17 + Spring Boot 3 code generation aligned with Explicit Architecture during SDD.

## Required Workflow

1. Inspect the repository instructions first:
   - `AGENTS.md`, `CLAUDE.md`, `GEMINI.md`, or equivalent agent instructions.
   - `docs/architecture/**`, `docs/testing**`, ADRs, and existing packages if present.
2. Read the relevant references in this skill:
   - For feature planning or implementation: `references/architecture-rules.md`.
   - For test planning, TDD, verification, or review: `references/testing-rules.md`.
3. Load code templates only when writing that exact kind of code:
   - Production code templates: `assets/templates/implementation/`.
   - Test code templates: `assets/templates/tests/`.
   - Do not load the whole templates tree; read only the file named by the relevant reference table.
4. Preserve the repository's local naming and package conventions when they are stricter than this skill.
5. During Superpowers SDD planning, require each task to state:
   - exact layer/package touched,
   - exact test type required,
   - whether it is command path, query path, adapter, interface, event, or schema work.
6. During TDD, write tests at the correct layer before implementation:
   - Domain rules: domain unit test first.
   - Handler orchestration: handler unit test first.
   - Adapter behavior: adapter integration test first.
   - Controller/consumer mapping: slice/unit test first.
   - Cross-boundary contract: contract/schema test first.
7. Before completion, review the generated code against the layer-boundary checklist in `architecture-rules.md` and the testing checklist in `testing-rules.md`.

## Non-Negotiable Rules

- Domain model depends on nothing outside itself.
- Domain services are pure logic: no repository/client/cache/message dependencies.
- Application handlers orchestrate; domain decides.
- Infrastructure implements ports; it contains framework and technology details.
- Interfaces translate external protocols into commands/queries; no business logic.
- Command results, query results, and external response DTOs are separate types.
- Command handlers must not return query results or external response DTOs.
- Read paths may bypass domain models; write paths must enforce domain invariants.
- Cross-boundary messages are contracts; domain events are local business facts.
- Tests must be layered: fast domain/application tests first, fewer adapter/component/contract/e2e tests later.

## Code Generation Defaults

Use Java 17 and Spring Boot 3:

- Use `record` for command, query, result, request, response, value-object-like DTOs, and events when appropriate.
- Use constructor injection.
- Use `@Service` for command/query handlers.
- Use `@RestController` only in interfaces packages.
- Use `@Repository`, `@Component`, `@ConfigurationProperties`, and `@Transactional` only in outer layers.
- Avoid field injection.
- Avoid framework behavior annotations in domain code.
- Prefer explicit mappers over leaking persistence entities into application or interfaces.

## Template Use

Use templates as copy-and-adapt starting points, not as mandatory final code. Replace all `{{PLACEHOLDER}}` markers with repository-specific names, remove irrelevant comments, and adjust imports to match the local package structure.

Do not paste every template into a plan. In SDD plans, reference the exact template path and include only the final code needed for that task.

## Output Expectations

When asked to plan or implement a feature, include:

- Layered file list grouped by Domain, Application, Infrastructure, Interfaces, and tests.
- Command/query split when both write and read behavior exist.
- Test-first order with exact test classes.
- Explicit note for any transaction boundary or cross-boundary event.
- A short architecture compliance check before final response.
