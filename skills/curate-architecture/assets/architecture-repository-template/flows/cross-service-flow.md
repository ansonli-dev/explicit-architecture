---
id: "{{FLOW_ID}}"
type: "ARCHITECTURE"
status: "{{STATUS}}"
authority: "{{MANDATORY|INFORMATIONAL}}"
owner: "{{OWNER}}"
scope: ["{{SCOPE}}"]
version: "{{VERSION}}"
last_reviewed: "{{DATE}}"
---

# {{FLOW_NAME}}

## Business Outcome and Participants

- Outcome: {{OUTCOME}}
- Trigger: {{TRIGGER}}
- Participants: {{PARTICIPANTS}}
- Process authority: {{PROCESS_AUTHORITY}}

## Authoritative Facts

| Fact / state | Authority | Local meaning | Advances which service | Evidence link |
|---|---|---|---|---|
| {{FACT}} | {{AUTHORITY}} | {{MEANING}} | {{SERVICE_RULE}} | {{EVIDENCE}} |

## Interaction

```mermaid
sequenceDiagram
    participant A as "{{SERVICE_A}}"
    participant B as "{{SERVICE_B}}"
    A->>B: "{{INTENT_OR_FACT}}"
    alt accepted
        B-->>A: "{{ACCEPTED_RESULT}}"
    else rejected
        B-->>A: "{{REJECTION}}"
    else unknown
        A->>B: "{{AUTHORITATIVE_QUERY}}"
        B-->>A: "{{RECONCILED_RESULT}}"
    end
```

## Failure and Recovery

| Failure | Prevent / detect / recover | Owner | Contract / Pattern |
|---|---|---|---|
| {{FAILURE}} | {{TREATMENT}} | {{OWNER}} | {{REFERENCE}} |

## Related Context

{{RELATED_ARCHITECTURE_STANDARD_PATTERN_ADR_AND_OWNER_LINKS}}
