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

subject_exists() {
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$SR_URL/subjects/$1/versions/latest")
    [ "$code" = "200" ]
}

register() {
    local subject=$1 avsc_path=$2
    if subject_exists "$subject"; then
        echo "  [SKIP] $subject — already registered"
        return 0
    fi
    local payload result schema_id
    payload=$(jq -c '{schema: (. | tojson)}' "$avsc_path")
    result=$(curl -sf -X POST \
        -H "Content-Type: application/vnd.schemaregistry.v1+json" \
        -d "$payload" \
        "$SR_URL/subjects/$subject/versions")
    schema_id=$(echo "$result" | jq -r '.id')
    echo "  [REG]  $subject — schema id: $schema_id"
}

wait_for_sr

errors=()
echo "=== Registering Avro schemas ==="
while IFS='=' read -r topic avsc_file; do
    subject="${topic}-value"
    if ! register "$subject" "$AVSC_DIR/$avsc_file"; then
        echo "  [ERR]  $subject" >&2
        errors+=("$subject")
    fi
done < <(parse_subjects)

echo ""
echo "=== Registered subjects ==="
curl -sf "$SR_URL/subjects" | jq -r '.[]' | sort | sed 's/^/  /'

if [ ${#errors[@]} -gt 0 ]; then
    echo ""
    echo "Failed subjects: ${errors[*]}" >&2
    exit 1
fi
echo "Done."
