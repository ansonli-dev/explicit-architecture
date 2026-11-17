# Capability As-Is — {{CAPABILITY_NAME}}

## Identity and Boundary

| Field | Current-ref fact | Evidence | Confidence |
|---|---|---|---|
| Capability ID | {{CAPABILITY_ID}} | {{EVIDENCE}} | `{{CONFIDENCE}}` |
| Purpose / design force | {{PURPOSE}} | {{EVIDENCE}} | `{{CONFIDENCE}}` |
| Module / effective version | {{MODULE_VERSION}} | {{EVIDENCE}} | `{{CONFIDENCE}}` |
| Owner | {{OWNER}} | {{EVIDENCE}} | `{{CONFIDENCE}}` |

## Contract

{{CONTRACT_INTERACTION_DIAGRAM}}

| Concern | Current contract |
|---|---|
| Input / trigger | {{INPUT_TRIGGER}} |
| Output / effect | {{OUTPUT_EFFECT}} |
| Guarantee / scope / point | {{GUARANTEE_SCOPE_POINT}} |
| Non-guarantees | {{NON_GUARANTEES}} |
| Failure / error semantics | {{FAILURE_SEMANTICS}} |

## Public Surface, Defaults and Extension

| Surface / configuration | Semantics | Default / precedence | Extension / consumer responsibility | Evidence |
|---|---|---|---|---|
| {{SURFACE}} | {{SEMANTICS}} | {{DEFAULT_PRECEDENCE}} | {{EXTENSION_RESPONSIBILITY}} | {{EVIDENCE}} |

## State, Lifecycle and Operations

{{STATE_LIFECYCLE_DIAGRAM}}

| State / worker / operation | Owner | Recovery / control | Observability | Evidence |
|---|---|---|---|---|
| {{STATE_WORKER_OPERATION}} | {{OWNER}} | {{RECOVERY_CONTROL}} | {{OBSERVABILITY}} | {{EVIDENCE}} |

删除本节，如果该能力没有持久状态、后台执行或运行操作。

## Compatibility, Consumers and Gaps

| Item | Current-ref fact | Evidence | Confidence |
|---|---|---|---|
| Compatibility / migration | {{COMPATIBILITY_MIGRATION}} | {{EVIDENCE}} | `{{CONFIDENCE}}` |
| Confirmed consumers / discovery | {{CONSUMERS}} | {{EVIDENCE}} | `{{CONFIDENCE}}` |
| Limitations | {{LIMITATIONS}} | {{EVIDENCE}} | `{{CONFIDENCE}}` |
| Known gaps | {{GAPS}} | {{EVIDENCE}} | `{{CONFIDENCE}}` |
