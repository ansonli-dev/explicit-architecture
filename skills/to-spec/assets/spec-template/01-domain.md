# Domain — {{FEATURE}}

## Inherited Constraints

{{INHERITED_CONSTRAINTS}}

## Ubiquitous Language

| Term | Definition | Owner | Not to be confused with |
|---|---|---|---|
| {{TERM}} | {{DEFINITION}} | {{OWNER}} | {{DISTINCTION}} |

## Aggregate Model

```mermaid
classDiagram
    class {{AGGREGATE}} {
        +{{BEHAVIOR}}()
    }
    class {{VALUE_OBJECT}}
    {{AGGREGATE}} *-- {{VALUE_OBJECT}}
```

| Element | Kind | Business responsibility | Requirement |
|---|---|---|---|
| {{ELEMENT}} | {{KIND}} | {{RESPONSIBILITY}} | {{REQ_ID}} |

## Lifecycle

```mermaid
stateDiagram-v2
    [*] --> {{INITIAL_STATE}}
    {{INITIAL_STATE}} --> {{NEXT_STATE}}: "{{TRIGGER}} [{{RULE_ID}}]"
    {{NEXT_STATE}} --> [*]
```

## Behaviors and Invariants

| Rule | Behavior / Guard | Rejection | Domain Event | Outer guarantee handoff |
|---|---|---|---|---|
| {{RULE_ID}} | {{BEHAVIOR}} | {{REJECTION}} | {{EVENT}} | {{HANDOFF}} |

## External References

| Reference | Local business meaning | Lifecycle use | Why Domain-owned |
|---|---|---|---|
| {{REFERENCE}} | {{MEANING}} | {{USE}} | {{RATIONALE}} |

## Write-side Persistence Semantics

| Capability | Domain signature | Purpose |
|---|---|---|
| {{CAPABILITY}} | {{SIGNATURE}} | {{PURPOSE}} |

## Mapping / Handoff

| Business semantic | Domain expression | Outer guarantee required | Must not enter Domain |
|---|---|---|---|
| {{SEMANTIC}} | {{EXPRESSION}} | {{GUARANTEE}} | {{TECHNICAL_DETAIL}} |

## Capability Insights

{{CAPABILITY_INSIGHTS}}

## Leakage Check

{{LEAKAGE_CHECK}}

## Open Questions

{{OPEN_QUESTIONS}}
