# Verification — {{FEATURE}}

## Requirement-to-Evidence Matrix

| Requirement / Rule | Domain evidence | Application evidence | Adapter / Contract evidence | Failure / migration evidence |
|---|---|---|---|---|
| {{REQ_ID}} | {{DOMAIN_TEST}} | {{APP_TEST}} | {{ADAPTER_TEST}} | {{FAILURE_TEST}} |

## Domain Verification

{{DOMAIN_VERIFICATION}}

## Application and Mapper Verification

{{APPLICATION_MAPPER_VERIFICATION}}

## Adapter and Contract Verification

{{ADAPTER_CONTRACT_VERIFICATION}}

## Concurrency and Failure Injection

| Scenario | Invariant / expected outcome | Test level | Evidence |
|---|---|---|---|
| {{SCENARIO}} | {{EXPECTED}} | {{LEVEL}} | {{EVIDENCE}} |

## External Event Robustness

| Duplicate | Out of order | Timeout / unknown | Retry / dead-letter | Reconciliation |
|---|---|---|---|---|
| {{DUPLICATE}} | {{ORDER}} | {{UNKNOWN}} | {{RETRY}} | {{RECONCILE}} |

## Migration and Rollback Verification

{{MIGRATION_VERIFICATION}}

## Exit Gates

- [ ] Every acceptance criterion is traceable.
- [ ] Persistence guarantees are verified with the actual selected storage engine and effective version.
- [ ] Version-sensitive implementation decisions are verified through the standard framework/data-access path.
- [ ] Mapper special rules are covered.
- [ ] Compatibility and rollback are exercised where applicable.
- [ ] No unresolved blocker changes the design boundary.
