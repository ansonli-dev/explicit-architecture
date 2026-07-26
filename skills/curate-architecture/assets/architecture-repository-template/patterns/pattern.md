---
id: "{{PATTERN_ID}}"
type: "PATTERN"
status: "{{DRAFT|ACTIVE|DEPRECATED|SUPERSEDED}}"
authority: "ADVISORY"
owner: "{{OWNER}}"
scope: ["{{SCOPE}}"]
applies_when: ["{{TRIGGER}}"]
version: "{{VERSION}}"
last_reviewed: "{{DATE}}"
---

# {{PATTERN_TITLE}}

## Problem and Forces

- Problem: {{PROBLEM}}
- Required guarantee: {{GUARANTEE}}
- Forces: {{FORCES}}
- Failure model: {{FAILURE_MODEL}}

## Applicability

- Use when: {{USE_WHEN}}
- Do not use when: {{DO_NOT_USE_WHEN}}
- Preconditions: {{PRECONDITIONS}}

## Stable Mechanism

{{TECHNOLOGY_NEUTRAL_MECHANISM}}

## Non-guarantees

{{NON_GUARANTEES}}

## Technology-sensitive Variants

| Variant | Required capability/version | Strength | Limitation | Standard-path impact |
|---|---|---|---|---|
| {{VARIANT}} | {{CAPABILITY}} | {{STRENGTH}} | {{LIMITATION}} | {{IMPACT}} |

## Evolution and Verification

- Compatibility/migration: {{EVOLUTION}}
- Operational considerations: {{OPERATIONS}}
- Verification: {{VERIFICATION}}
- Related Standards/Capabilities/ADRs: {{RELATED}}
