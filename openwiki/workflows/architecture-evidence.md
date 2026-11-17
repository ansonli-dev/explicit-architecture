---
type: workflow
title: Architecture evidence and specification workflow
description: Maps the repository's architecture skills, authority and baseline distinctions, specification templates, and structural validator to their intended tasks.
tags: [architecture, skills, evidence, specifications, governance]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-431d63762f1a09c22cc49192
    resource: repo://skills/_standards/explicit-architecture/baseline-ref-profile.md
  - id: openwiki-source-f19b0421eaa6c32124281917
    resource: repo://skills/_standards/explicit-architecture/scripts/validate_package.py
  - id: openwiki-source-68e3396848d7ecd3442f452f
    resource: repo://skills/curate-architecture/SKILL.md
  - id: openwiki-source-2bcf9d678335ec00f73751e9
    resource: repo://skills/review-codebase/SKILL.md
  - id: openwiki-source-c7894dc71c19828d756c432c
    resource: repo://skills/review-spec/SKILL.md
  - id: openwiki-source-8fdc66a740d305353979a017
    resource: repo://skills/sync-as-is/SKILL.md
  - id: openwiki-source-4b24162c65ad1a41844f8c52
    resource: repo://skills/sync-capability/SKILL.md
  - id: openwiki-source-fc2228e91e107728bde2aea6
    resource: repo://skills/to-spec/SKILL.md
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Architecture evidence and specification workflow

Alongside the bookstore example, `skills/` distributes an architecture workflow suite. Its source artifacts are skill instructions, shared standards, reference playbooks, document templates, and a Python package validator. This page describes that toolchain; generating OpenWiki does not execute these workflows or promote the working tree into a canonical architecture baseline.

## Choose the owner and artifact

| Task | Skill | Output and boundary |
|---|---|---|
| Organize central architectural knowledge | [curate-architecture](../../skills/curate-architecture/SKILL.md) | Landscape, standards, patterns, catalogs, ADR/index organization; does not author service Current or feature Target |
| Establish a deployable service's actual architecture | [sync-as-is](../../skills/sync-as-is/SKILL.md) | Layered service baseline, evidence, drift/delta, or verified promotion |
| Establish shared-library/SDK contracts | [sync-capability](../../skills/sync-capability/SKILL.md) | Module/capability baseline from a fixed capability Current ref |
| Design an unimplemented feature | [to-spec](../../skills/to-spec/SKILL.md) | Inside-out Target, Current-to-Target changes, integration and verification design |
| Review a proposed feature design | [review-spec](../../skills/review-spec/SKILL.md) | Evidence-backed findings and revision guidance; no automatic rewrite |
| Review implemented business-service code | [review-codebase](../../skills/review-codebase/SKILL.md) | Prioritized findings and governance/As-Is delta candidates; no automatic canonical-baseline update |

These boundaries matter in this mixed repository: `seedwork` is a shared capability, while Order, Catalog, and Notification are deployable business services. The skill suite explicitly separates shared-owner guarantees from how a service adopts a particular version/configuration.

## Authority and Current are separate

The suite distinguishes mandatory standards, advisory patterns, verified Current evidence, approved Target decisions, and implementation evidence. Feature design consumes central Architecture Context read-only. A missing authority or contract that could change boundaries, ownership, state, or consistency becomes a named gap and may block Target design; existing code cannot automatically supply that missing authority.

The shared ref profile maps roles to repository refs. Defaults are:

- Service Current: the last verified commit on the `test` verification ref; `develop` holds development changes.
- Capability Current: a fixed full SHA on `develop`.
- Feature delta: a supplied feature ref relative to its actual merge base.

These are configurable suite defaults, not evidence that this repository has those branches or that their tips are verified. The profile resolution order is explicit user profile, canonical repository workflow, then suite default. The selected refs must be pinned to full SHAs; changing a profile does not itself promote a baseline.

Service synchronization records the environment, status, evidence time, and predecessor. It distinguishes confirmed, inferred, and unknown facts, and classifies differences such as implementation drift, stale documentation, approved change, transition state, environment drift, and evidence gap. Promotion requires previous verified baseline, immutable candidate, approved decisions, and deployment/test/runtime evidence. Capability synchronization instead restricts Current research to its resolved owner ref.

## Feature design flow

`to-spec` establishes scope and authoritative inputs, traces requirements through decisions and verification, compares materially different candidates, then designs Domain → Application → Interfaces/Infrastructure/Integration. Candidate selection records rationale, residual risk, fallback, invalidation conditions, and verification. Reuse and mechanism choices precede framework-level implementation choices.

The default spec assets cover context, domain, application, interfaces, infrastructure, integration contracts, verification, codebase evolution, and traceability. Existing canonical layouts take precedence. Evolution uses explicit actions such as KEEP, MODIFY, ADD, MOVE, RENAME, REPLACE, and DELETE. Templates contain placeholders and are starting material, not completed packages.

## Deterministic package validation

[validate_package.py](../../skills/_standards/explicit-architecture/scripts/validate_package.py) uses only Python standard-library imports. Invoke it on a completed package:

```bash
python3 skills/_standards/explicit-architecture/scripts/validate_package.py --profile spec --layout template /path/to/spec
python3 skills/_standards/explicit-architecture/scripts/validate_package.py --profile service-as-is --layout template /path/to/as-is
python3 skills/_standards/explicit-architecture/scripts/validate_package.py --self-test
```

The CLI offers `spec` and `service-as-is` profiles and `template` or `existing` layouts. Common checks find unresolved placeholders, broken relative file links, and near-empty Markdown. Template mode additionally checks required filenames. Spec checks inspect candidate verdict/selection fields and evolution-action tokens. Service baseline checks validate a full SHA, recognized status, and environment in the manifest. Some sparse-content and traceability findings are warnings; errors return exit code 1.

`--layout existing` relaxes template-specific checks; it is not an equivalent certification of a differently structured package. The validator does not resolve cited commits, verify deployment evidence, approve an architecture, or establish that a candidate comparison is substantively sound. The skill instructions retain a semantic completion gate after structural validation. Its built-in self-test uses temporary fixtures for valid and invalid packages; it passed during wiki initialization.

## Related reading

Use the [system map](../architecture/system-map.md) for implemented bookstore behavior and [test map](../testing/verification-map.md) for runtime evidence. The [ref-profile standard](../../skills/_standards/explicit-architecture/baseline-ref-profile.md) and [spec context template](../../skills/to-spec/assets/spec-template/00-context.md) are the best starting points for understanding evidence manifests. [Quickstart](../quickstart.md) routes between the runtime and architecture-tooling parts of the repository.
