#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
scratch=$(mktemp -d "${TMPDIR:-/tmp}/bookstore-keycloak.XXXXXX")
suffix=$(printf '%s' "${scratch##*.}" | tr '[:upper:]' '[:lower:]')
compose=(docker compose -p "bookstore-keycloak-${suffix}" -f "$script_dir/../compose.yaml" -f "$scratch/ports.yaml")

cleanup() {
    local status=$?
    if (( status != 0 )); then
        "${compose[@]}" ps --all >&2 || true
        "${compose[@]}" logs --no-color keycloak mailpit >&2 || true
    fi
    "${compose[@]}" down --volumes || status=1
    rm -rf -- "$scratch"
    exit "$status"
}

# Keep the demo issuer and use Docker-assigned host ports for the smoke test.
cat > "$scratch/ports.yaml" <<'YAML'
services:
  keycloak:
    ports: !override
      - target: 8080
        host_ip: 127.0.0.1
  mailpit:
    ports: !reset []
YAML
trap cleanup EXIT

"${compose[@]}" up --detach --wait --wait-timeout 150 keycloak
binding=$("${compose[@]}" port keycloak 8080)
port=${binding##*:}
request=(curl --fail-with-body --silent --show-error --max-time 15
    --noproxy localhost --connect-to "localhost:8180:127.0.0.1:$port")
origin=http://localhost:8180
issuer="$origin/realms/bookstore"
authorization="$issuer/protocol/openid-connect/auth"

if ! discovery=$("${request[@]}" "$issuer/.well-known/openid-configuration") ||
    ! jq --exit-status --arg issuer "$issuer" --arg authorization "$authorization" \
        '.issuer == $issuer and .authorization_endpoint == $authorization' <<< "$discovery" >/dev/null; then
    printf 'Unexpected bookstore realm metadata:\n%s\n' "$discovery" >&2
    exit 1
fi

for locale in en zh-CN; do
    if ! page=$("${request[@]}" --get "$authorization" \
        --data-urlencode 'client_id=bookstore-bff' \
        --data-urlencode 'response_type=code' \
        --data-urlencode 'scope=openid' \
        --data-urlencode 'redirect_uri=http://localhost:3000/api/auth/oauth2/callback/keycloak' \
        --data-urlencode 'state=realm-import-check' \
        --data-urlencode 'code_challenge_method=S256' \
        --data-urlencode 'code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM' \
        --data-urlencode "ui_locales=$locale"); then
        printf 'Keycloak authorization request failed (%s):\n%s\n' "$locale" "$page" >&2
        exit 1
    fi

    stylesheet=$(sed -nE 's/.*href="(\/resources\/[^/]+\/login\/bookstore\/css\/bookstore\.css)".*/\1/p' <<< "$page")
    if [[ -z "$stylesheet" ]] ||
        ! grep -Fq "lang=\"$locale\"" <<< "$page" ||
        ! grep -Fq '/login-actions/registration?' <<< "$page" ||
        ! grep -Fq '/login-actions/reset-credentials?' <<< "$page" ||
        ! grep -Fq "href=\"http://localhost:3000/$locale/books\"" <<< "$page"; then
        printf 'Unexpected Keycloak login page for %s:\n%s\n' "$locale" "$page" >&2
        exit 1
    fi

    if ! css=$("${request[@]}" "$origin$stylesheet") ||
        ! grep -Fq -- '--bookstore-primary:' <<< "$css"; then
        printf 'Bookstore stylesheet failed to load (%s):\n%s\n' "$locale" "$css" >&2
        exit 1
    fi
    printf 'Fresh bookstore realm verified (%s).\n' "$locale"
done
