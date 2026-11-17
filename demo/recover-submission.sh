#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo 'Usage: bash demo/recover-submission.sh inspect SUBMISSION_ID' >&2
    echo '       bash demo/recover-submission.sh {resume|compensate} SUBMISSION_ID OBSERVED_CLAIM_VERSION' >&2
    exit 2
}

action=${1:-}
id=${2:-}
[[ $id =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] || usage
compose=(docker compose -f "$(dirname "${BASH_SOURCE[0]}")/compose.yaml")

if [[ $action == inspect && $# == 2 ]]; then
    "${compose[@]}" exec -T order-db psql -X -v ON_ERROR_STOP=1 -v "submission_id=$id" -U bookstore -d order <<'SQL'
SELECT id, state, reason, attempt_count, next_attempt_at, lease_until, claim_version
FROM checkout_submission WHERE id = :'submission_id'::uuid;
SELECT id, status, version, reservation_protocol FROM orders WHERE id = :'submission_id'::uuid;
SQL
    "${compose[@]}" exec -T catalog-db psql -X -v ON_ERROR_STOP=1 -v "submission_id=$id" -U bookstore -d catalog <<'SQL'
SELECT submission_id, state, reason FROM checkout_reservation WHERE submission_id = :'submission_id'::uuid;
SQL
    exit
fi

[[ $# == 3 && $3 =~ ^[0-9]+$ ]] || usage
case $action in
    resume) state=PROCESSING ;;
    compensate) state=COMPENSATING ;;
    *) usage ;;
esac

changed=$("${compose[@]}" exec -T order-db psql -X -qAt -v ON_ERROR_STOP=1 \
    -v "submission_id=$id" -v "observed_fence=$3" -v "next_state=$state" -U bookstore -d order <<'SQL'
UPDATE checkout_submission
SET state = :'next_state', claim_version = claim_version + 1,
    lease_until = NULL, attempt_count = 0, next_attempt_at = clock_timestamp()
WHERE id = :'submission_id'::uuid
  AND state = 'RECONCILE_REQUIRED' AND claim_version = :'observed_fence'::bigint
RETURNING id, state, claim_version;
SQL
)
if [[ -z $changed ]]; then
    echo 'Submission or claim version changed. Inspect it again before retrying.' >&2
    exit 1
fi
printf '%s\n' "$changed"
