# Recover a checkout submission

Run these commands from the repository root with the same `COMPOSE_PROJECT_NAME` used to start the demo. Use the submission ID returned by the API or printed in the Order worker log.

```sh
bash demo/recover-submission.sh inspect "$SUBMISSION_ID"
```

`PROCESSING` covers both due submissions and active ten-second claims. The worker uses the same reservation ID after a timeout or restart. Six failed attempts, including five retries at 1, 2, 4, 8 and 16 seconds, lead to `RECONCILE_REQUIRED`. The check also shows whether Catalog recorded `HELD`, `REJECTED` or `RELEASED` and whether Order committed the aggregate. The outputs omit the buyer's identity, email, and bearer token.

When Catalog is reachable and the Order row is still awaiting reconciliation, resume with the inspected `claim_version`.

```sh
bash demo/recover-submission.sh resume "$SUBMISSION_ID" "$CLAIM_VERSION"
```

If the submission must be abandoned, transition it to compensation with that same version check.

```sh
bash demo/recover-submission.sh compensate "$SUBMISSION_ID" "$CLAIM_VERSION"
```

The transition commits the new fence before the worker can release the hold. Catalog records a tombstone even when no hold exists. While compensation retries, the API reports `RECONCILE_REQUIRED`. After release succeeds, it reports `REJECTED` with `PLACEMENT_ABORTED`. A placed order uses the standard v2 cancellation endpoint and Kafka release path.

Keep both databases and the Kafka consumer group when closing intake. `DEMO_V2_INTAKE_ENABLED=false` still allows existing key replays, polling, worker recovery and cancellation. Keep the compatible Catalog consumer active for every live v2 order and replayable cancellation event.

## Verify a restart after Catalog commits

Use the disposable verification project with intake open and the last-copy fixture available. Run the normal verification first to populate the runner's Gradle cache. Run this after reopening intake if you completed the closed-intake phase.

```sh
COMPOSE_PROJECT_NAME=bookstore-verify bash demo/verify/order-restart.sh
```

The check takes a short-lived PostgreSQL lock that permits reads and holds the final Order insert. The test first observes a committed Catalog reservation and the absence of a committed order. The script terminates and restarts the verification Order process. The second test observes `PLACED` at the original ID, verifies replay and a single decrement, then cancels and waits for the inventory to return.
