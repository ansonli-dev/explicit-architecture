#!/usr/bin/env bash
# Registers Avro schemas in Confluent Schema Registry (idempotent).
# Reads topic→schema mapping from topics.yaml; schema data from /schemas/*.avsc.
# Subject naming follows TopicNameStrategy: <topic-name>-value.
# Requires: curl, jq, awk.
# Environment variables:
#   SCHEMA_REGISTRY_URL  e.g. http://schema-registry.infra.svc.cluster.local:8081
#   TOPICS_YAML          path to mounted topics.yaml (default: /config/topics.yaml)
#   AVSC_DIR             path to mounted .avsc files  (default: /schemas)
set -euo pipefail

SR_URL="${SCHEMA_REGISTRY_URL:-http://schema-registry.infra.svc.cluster.local:8081}"
TOPICS_YAML="${TOPICS_YAML:-/config/topics.yaml}"
AVSC_DIR="${AVSC_DIR:-/schemas}"

# Outputs lines of the form: <topic-name>=<avsc-filename>
parse_subjects() {
    awk '
        /^  - name: /    { split($0, a, ": "); gsub(/[ \t\r\n]/, "", a[2]); topic = a[2] }
        /^    valueSchema:/ { split($0, a, ": "); gsub(/[ \t\r\n]/, "", a[2])
                              n = split(a[2], parts, "/"); print topic "=" parts[n] }
    ' "$TOPICS_YAML"
}

wait_for_sr() {
    echo "Waiting for Schema Registry at $SR_URL ..."
    until curl -sf "$SR_URL/subjects" > /dev/null 2>&1; do
        echo "  not ready, retrying in 5s..."
        sleep 5
    done
    echo "Schema Registry is ready."
    echo ""
}

post_schema() {
    local endpoint=$1 payload=$2
    shift 2
    curl -sS -H "Content-Type: application/vnd.schemaregistry.v1+json" \
        --data "$payload" "$@" "$SR_URL/$endpoint"
}

schema_id() {
    jq -er '.id | select(type == "number" and . == floor and . > 0)'
}

register() {
    local subject=$1 avsc_path=$2
    local payload lookup status body error_code compatibility result id verified_id
    payload=$(jq -ce '{schema: tojson}' "$avsc_path") || return 1
    lookup=$(post_schema "subjects/$subject" "$payload" -w $'\n%{http_code}') || return 1
    status=${lookup##*$'\n'}
    body=${lookup%$'\n'*}

    if [ "$status" = 200 ]; then
        id=$(schema_id <<< "$body") || return 1
        echo "  [SKIP] $subject — schema id: $id"
        return 0
    fi

    error_code=$(jq -r '.error_code' <<< "$body") || return 1
    case "$status:$error_code" in
        404:40401) ;; # First version of this subject
        404:40403)
            compatibility=$(post_schema "compatibility/subjects/$subject/versions/latest?verbose=true" "$payload" -f) || return 1
            if ! jq -e '.is_compatible == true' <<< "$compatibility" >/dev/null; then
                echo "  Incompatible schema for $subject: $compatibility" >&2
                return 1
            fi
            ;;
        *)
            echo "  Schema lookup failed for $subject (HTTP $status): $body" >&2
            return 1
            ;;
    esac

    result=$(post_schema "subjects/$subject/versions" "$payload" -f) || return 1
    id=$(schema_id <<< "$result") || return 1
    lookup=$(post_schema "subjects/$subject" "$payload" -f) || return 1
    verified_id=$(schema_id <<< "$lookup") || return 1
    if [ "$verified_id" != "$id" ]; then
        echo "  Expected schema id $id for $subject, found $verified_id" >&2
        return 1
    fi
    echo "  [REG]  $subject — schema id: $id"
}

subjects=$(parse_subjects)
if [ -z "$subjects" ]; then
    echo "No schemas are configured in $TOPICS_YAML." >&2
    exit 1
fi
wait_for_sr

errors=""
echo "=== Registering Avro schemas ==="
while IFS='=' read -r topic avsc_file; do
    subject="${topic}-value"
    if ! register "$subject" "$AVSC_DIR/$avsc_file"; then
        echo "  [ERR]  $subject" >&2
        errors="$errors $subject"
    fi
done <<< "$subjects"

echo ""
echo "=== Registered subjects ==="
curl -sf "$SR_URL/subjects" | jq -r '.[]' | sort | sed 's/^/  /'

if [ -n "$errors" ]; then
    echo ""
    echo "Failed subjects:$errors" >&2
    exit 1
fi
echo "Done."
