#!/usr/bin/env bash
# Creates Kafka topics defined in topics.yaml (idempotent via --if-not-exists).
# Environment variables:
#   KAFKA_BOOTSTRAP_SERVERS  e.g. kafka.infra.svc.cluster.local:29092
#   TOPICS_YAML              path to mounted topics.yaml (default: /config/topics.yaml)
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:?KAFKA_BOOTSTRAP_SERVERS is required}"
TOPICS_YAML="${TOPICS_YAML:-/config/topics.yaml}"

extract_topics() {
    grep "  - name:" "$TOPICS_YAML" | sed 's/  - name: //'
}

extract_field() {
    local topic_name=$1 field_name=$2
    awk -v topic="$topic_name" -v field="$field_name" '
        $0 ~ "  - name: " topic { found=1; next }
        found && $0 ~ "  - name: " { found=0 }
        found && $0 ~ "    " field ":" {
            sub("    " field ": ", "", $0)
            gsub(/^"|"$/, "", $0)
            print $0; exit
        }
    ' "$TOPICS_YAML"
}

echo "Waiting for Kafka at $BOOTSTRAP ..."
until kafka-topics --bootstrap-server "$BOOTSTRAP" --list > /dev/null 2>&1; do
    echo "  not ready, retrying in 5s..."
    sleep 5
done
echo "Kafka is ready."
echo ""
echo "=== Creating topics ==="
for name in $(extract_topics); do
    partitions=$(extract_field "$name" "partitions"); partitions=${partitions:-3}
    rf=$(extract_field "$name" "replicationFactor");   rf=${rf:-1}
    echo -n "  $name (partitions=$partitions, rf=$rf) ... "
    kafka-topics --bootstrap-server "$BOOTSTRAP" \
        --create --if-not-exists \
        --topic "$name" --partitions "$partitions" --replication-factor "$rf" \
        && echo "OK"
done
echo ""
echo "=== Current topics ==="
kafka-topics --bootstrap-server "$BOOTSTRAP" --list | grep "^bookstore\." || true
echo "Done."
