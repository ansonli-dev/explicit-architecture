#!/usr/bin/env bash
set -euo pipefail

: "${COMPOSE_PROJECT_NAME:?Set COMPOSE_PROJECT_NAME to the disposable verification project}"
demo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
export COMPOSE_FILE="${COMPOSE_FILE:-$demo_dir/compose.yaml}"
export DEMO_VERIFY_CRASH_RECOVERY=true

order_sql() {
    docker compose exec -T order-db psql -X -qAt -v ON_ERROR_STOP=1 -U bookstore -d order -c "$1"
}

release_test_lock() {
    order_sql "SELECT pg_cancel_backend(pid) FROM pg_stat_activity
        WHERE application_name = 'bookstore-restart-fixture' AND datname = current_database()" >/dev/null
}

run_case() {
    docker compose --profile verify run --rm --no-deps -T e2e ./gradlew --offline --no-daemon \
        test --rerun --tests "*OrderV2CancellationE2ETest.$1"
}

docker compose exec -T -e PGAPPNAME=bookstore-restart-fixture order-db \
    psql -X -q -v ON_ERROR_STOP=1 -U bookstore -d order \
    -c 'BEGIN; LOCK TABLE orders IN SHARE MODE; SELECT pg_sleep(180); ROLLBACK;' &
lock_process=$!
trap 'release_test_lock; wait "$lock_process" || true' EXIT

locked='no'
for attempt in {1..15}; do
    locked=$(order_sql "SELECT EXISTS (SELECT 1 FROM pg_locks l JOIN pg_stat_activity a USING (pid)
        WHERE l.relation = 'orders'::regclass AND l.mode = 'ShareLock' AND l.granted
          AND a.application_name = 'bookstore-restart-fixture')")
    [[ $locked == t ]] && break
    sleep 1
done
[[ $locked == t ]] || { echo 'Orders test lock was not acquired' >&2; exit 1; }

run_case prepareCrashRecovery
blocked=$(order_sql "SELECT EXISTS (SELECT 1 FROM pg_locks
    WHERE relation = 'orders'::regclass AND mode = 'RowExclusiveLock' AND NOT granted)")
[[ $blocked == t ]] || { echo 'The Order insert did not reach the test lock' >&2; exit 1; }

docker compose kill -s SIGKILL order
release_test_lock
wait "$lock_process" || true
trap - EXIT

docker compose up -d --wait --no-deps order
run_case verifyCrashRecovery
