# Bookstore v2 integration demo

Run from the repository root with JDK 21, Docker Compose v2 or newer, and access to the image and Gradle repositories. The service modules use their existing Gradle wrappers and Maven Local. Build the Spring Boot jars before starting Compose.

```bash
(cd seedwork && ./gradlew publishToMavenLocal)
(cd shared-events && ./gradlew test publishToMavenLocal)
(cd catalog && ./gradlew bootJar)
(cd order && ./gradlew bootJar)
docker compose -f demo/compose.yaml config --quiet
docker compose -f demo/compose.yaml up -d --wait
```

Compose starts PostgreSQL for each bounded context, Redis, Elasticsearch, Keycloak, Kafka, and Schema Registry. One-shot jobs create topics and register exact schemas before Catalog starts. Catalog runs Flyway before its health check passes, then the seed job runs. Order starts with the scheduler relay.

Run the seed again with the one-shot job.

```bash
docker compose -f demo/compose.yaml run --rm --no-deps -T catalog-seed
```

The seed inserts 13 CNY books across Architecture and Engineering. Conflicting IDs keep their existing titles, versions, and stock. Inspect the single-copy fixture on the private network.

```bash
docker run --rm --network bookstore-demo_default curlimages/curl:8.11.1 \
  -fsS http://catalog:8081/api/v2/books/00000000-0000-4000-8000-00000000010d
```

Rebuild the affected jar and recreate its container after Java changes. Use `docker compose -f demo/compose.yaml down` to stop the stack and keep the PostgreSQL, Kafka, and search volumes.

## Frontend handoff

Join the BFF container to the `bookstore-demo_default` network. A project name supplied with `-p` changes the network name to `<project>_default`. Publish the frontend on loopback at port 3000. The browser reaches the frontend and Keycloak through loopback. Java HTTP, database, Kafka, and Registry traffic stays on Docker's project network.

| Setting | Value |
| --- | --- |
| Public issuer | `http://localhost:8180/realms/bookstore` |
| Authorization endpoint | `http://localhost:8180/realms/bookstore/protocol/openid-connect/auth` |
| Internal token, userinfo and JWKS endpoints | `http://keycloak:8080/realms/bookstore/protocol/openid-connect/{token,userinfo,certs}` |
| BFF client ID and local secret | `bookstore-bff` / `bookstore-bff-local` |
| Exact callback | `http://localhost:3000/api/auth/oauth2/callback/keycloak` |
| Login flow and scopes | Authorization Code with S256 PKCE and state. Request `openid email profile`. |
| Catalog and Order base URLs | `http://catalog:8081` and `http://order:8082` |
| Catalog and Order API audiences | `bookstore-catalog` and `bookstore-order-api` |

Validate tokens against the public issuer, even when retrieving keys and redeeming a code over the private network. Forward the BFF's access token as a bearer token. Reader tokens carry the canonical UUID `sub`, `email_verified: true`, and the `bookstore-reader` realm role. The `bookstore-admin` role permits Catalog administration. Order obtains a separate client-credentials token as `bookstore-order` with audience `bookstore-catalog-internal` and role `bookstore-order-service`.

| Login | Local password | Role |
| --- | --- | --- |
| `reader-a` | `reader-a-local` | Customer |
| `reader-b` | `reader-b-local` | Customer |
| `admin` | `admin-local` | Catalog administrator |

The realm, fixture credentials, and plaintext listeners are for the isolated local demo.

## Verification

Run the existing Catalog and Order test suites separately from their module directories with `./gradlew test`. Their integration tests require Docker for Testcontainers. Use a fresh Compose project for the private-network verification, with any other stack on port 8180 stopped. The runner authenticates against Keycloak using Code + PKCE and exercises the real Java, PostgreSQL, and Kafka services. It stores synthetic checkout IDs in a project volume to carry the same submission across the Order restart.

```bash
docker compose -p bookstore-verify -f demo/compose.yaml up -d --wait
docker compose -p bookstore-verify -f demo/compose.yaml --profile verify run --rm -T e2e
DEMO_V2_INTAKE_ENABLED=false docker compose -p bookstore-verify -f demo/compose.yaml \
  up -d --wait --no-deps --force-recreate order
DEMO_VERIFY_INTAKE_CLOSED=true docker compose -p bookstore-verify -f demo/compose.yaml \
  --profile verify run --rm --no-deps -T e2e
docker compose -p bookstore-verify -f demo/compose.yaml down
```

The first phase checks public paging, administrator title versions, role isolation, v1 write rejection, same-key replay, and concurrent checkouts for one copy. The second checks that the original submission survives closing intake, new work receives 503, and the owner can still cancel. It republishes the original Avro cancellation, waits for Catalog to commit the replay offset, and checks that the replacement hold keeps its stock.

For the process crash check, use the [restart verification](recovery.md#verify-a-restart-after-catalog-commits). The [verification record](verification.md) captures the module tests, live fault cases, and runtime versions.

For a Registry upgrade check, use a disposable project and point `DEMO_AVRO_DIR` at an absolute directory containing the previous release's Avro files. Register them before running the current initializer against the same broker and Registry.

```bash
DEMO_AVRO_DIR="$PREVIOUS_AVRO_DIR" docker compose -p bookstore-schema-check -f demo/compose.yaml \
  run --rm -T schema-init
docker compose -p bookstore-schema-check -f demo/compose.yaml run --rm -T schema-init
docker compose -p bookstore-schema-check -f demo/compose.yaml run --rm -T schema-init
```

On upgrading from shared-events 0.1.0, the second run should register a new ID for `bookstore.order.cancelled-value`. The third should find that exact schema ID. Start the compatible Catalog consumer before enabling the upgraded Order writer.

To try this catalog with v1, set `LEGACY_CATALOG_DB_URL` to a separate local PostgreSQL database that the legacy Catalog service has migrated.

```bash
psql "$LEGACY_CATALOG_DB_URL" -v ON_ERROR_STOP=1 -f demo/seed/catalog.sql
curl -fsS 'http://localhost:8081/api/v1/books?page=0&size=20'
```

## Intake and recovery

Set `DEMO_V2_INTAKE_ENABLED=false` and recreate Order to suspend new quotes and submissions.

```bash
DEMO_V2_INTAKE_ENABLED=false docker compose -f demo/compose.yaml up -d --wait --no-deps --force-recreate order
```

Correct replays, customer queries, cancellation, and background processing continue. For a Catalog title-edit pause, recreate Catalog with `DEMO_V2_ADMIN_WRITES_ENABLED=false`. Keep the v2-compatible Catalog consumer and its reservation records in service while v2 orders or cancellation events remain. See [submission recovery](recovery.md) for resuming or compensating a durable submission.
