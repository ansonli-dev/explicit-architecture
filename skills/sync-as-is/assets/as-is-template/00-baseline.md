# As-Is Baseline — {{BASELINE_ID}}

## Manifest

| Field | Value |
|---|---|
| Ref Profile / source | `{{REF_PROFILE_ID}} / {{REF_PROFILE_SOURCE}}` |
| Verification ref | `{{VERIFICATION_REF}}` |
| Development ref | `{{DEVELOPMENT_REF}}` |
| Commit | `{{FULL_SHA}}` |
| Environment | `{{ENVIRONMENT}}` |
| Status | `{{VERIFIED_CANDIDATE_PARTIAL_STALE}}` |
| Verified at | {{VERIFIED_AT}} |
| Supersedes | {{PREVIOUS_BASELINE}} |
| Evidence scope | {{EVIDENCE_SCOPE}} |

## Verification Evidence

| Evidence | Result | Ref / timestamp | Confidence |
|---|---|---|---|
| {{EVIDENCE}} | {{RESULT}} | {{REF_TIMESTAMP}} | `{{CONFIDENCE}}` |

## Branch Delta Context

| View | From | To | Merge-base | Status |
|---|---|---|---|---|
| Pending Develop Delta | `{{BASELINE_SHA}}` | `{{DEVELOPMENT_SHA}}` | {{DEVELOPMENT_MERGE_BASE}} | `{{DELTA_STATUS}}` |
| Feature Delta | `{{FEATURE_MERGE_BASE}}` | `{{FEATURE_SHA}}` | {{FEATURE_MERGE_BASE}} | `{{FEATURE_STATUS}}` |
| Test Candidate Delta | `{{BASELINE_SHA}}` | `{{TEST_CANDIDATE_SHA}}` | {{TEST_MERGE_BASE}} | `{{CANDIDATE_STATUS}}` |

## Known Gaps

| Gap | Affected scope | Consequence | Owner / next evidence |
|---|---|---|---|
| {{GAP}} | {{SCOPE}} | {{CONSEQUENCE}} | {{OWNER_EVIDENCE}} |
