#!/usr/bin/env bash
# Applies Kafka ACLs defined in topics.yaml (idempotent via --add).
# Producer principals get WRITE + DESCRIBE on their topics.
# Consumer principals get READ + DESCRIBE on their topics, and READ on groups
# with prefix "{service}." (PREFIXED pattern) — wrong group names are rejected by the broker.
# Environment variables:
#   KAFKA_ADMIN_BOOTSTRAP_SERVERS  e.g. kafka.infra.svc.cluster.local:9092
#   KAFKA_ADMIN_USERNAME
#   KAFKA_ADMIN_PASSWORD
#   TOPICS_YAML                    path to mounted topics.yaml (default: /config/topics.yaml)
set -euo pipefail

ADMIN_BOOTSTRAP="${KAFKA_ADMIN_BOOTSTRAP_SERVERS:?KAFKA_ADMIN_BOOTSTRAP_SERVERS is required}"
TOPICS_YAML="${TOPICS_YAML:-/config/topics.yaml}"

CLIENT_CONFIG=$(mktemp)
cat > "$CLIENT_CONFIG" << EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username="${KAFKA_ADMIN_USERNAME}" password="${KAFKA_ADMIN_PASSWORD}";
EOF
trap 'rm -f $CLIENT_CONFIG' EXIT

ACLS_CMD="kafka-acls --bootstrap-server $ADMIN_BOOTSTRAP --command-config $CLIENT_CONFIG"

# Map consumer identifier → SASL principal.
# "order-read-model" is the read-model projection running inside the order service.
consumer_principal() {
    case "$1" in
        order-read-model) echo "order" ;;
        *) echo "$1" ;;
    esac
}

extract_topics() {
    grep "  - name:" "$TOPICS_YAML" | sed 's/  - name: //'
}

extract_list() {
    local topic_name=$1 field_name=$2
    awk -v topic="$topic_name" -v field="$field_name" '
        $0 ~ "  - name: " topic { found_topic=1; next }
        found_topic && $0 ~ "  - name: " { found_topic=0 }
        found_topic && $0 ~ "    " field ":" { found_field=1; next }
        found_field && $0 ~ "    [a-zA-Z]+:" { found_field=0 }
        found_field && $0 ~ "      - " {
            sub("      - ", "", $0)
            gsub(/^"|"$/, "", $0)
            print $0
        }
    ' "$TOPICS_YAML"
}

echo "=== Applying Topic ACLs ==="
for name in $(extract_topics); do
    for p in $(extract_list "$name" "producers"); do
        echo -n "  WRITE: User:$p -> $name ... "
        $ACLS_CMD --add --allow-principal "User:$p" \
            --operation WRITE --operation DESCRIBE --topic "$name" && echo "OK"
    done
    for c in $(extract_list "$name" "consumers"); do
        principal=$(consumer_principal "$c")
        echo -n "  READ:  User:$principal -> $name ... "
        $ACLS_CMD --add --allow-principal "User:$principal" \
            --operation READ --operation DESCRIBE --topic "$name" && echo "OK"
    done
done

echo ""
echo "=== Applying Consumer Group ACLs (PREFIXED) ==="
# Each service may only join groups whose name starts with "{service}.".
# Using PREFIXED pattern means any other group name triggers GroupAuthorizationException.
declare -A seen_principals
for name in $(extract_topics); do
    for c in $(extract_list "$name" "consumers"); do
        principal=$(consumer_principal "$c")
        if [[ -z "${seen_principals[$principal]+_}" ]]; then
            seen_principals[$principal]=1
            echo -n "  GROUP: User:$principal -> prefix \"${principal}.\" ... "
            $ACLS_CMD --add --allow-principal "User:$principal" \
                --operation READ \
                --group "${principal}." \
                --resource-pattern-type PREFIXED && echo "OK"
        fi
    done
done

echo ""
echo "=== Current ACLs ==="
$ACLS_CMD --list --topic '*' 2>/dev/null || true
echo "Done."
