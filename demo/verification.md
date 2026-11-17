# Java v2 verification

Verified on 2026-11-17 against the Java source baseline `14e511a8f361616e166f23daf5184012d501c656`. The repository's remote `main` still pointed to that commit at verification time. [Issue #1](https://github.com/ansonli-dev/explicit-architecture/issues/1)

## Module tests

| Module | Passed | Evidence |
| --- | ---: | --- |
| Catalog | 86 | Legacy REST and component tests, signed JWT rejection, PostgreSQL migration, deterministic pages, edit conflicts, reservation races, and Kafka redelivery after a database failure |
| Order | 107 | Legacy REST and component tests, signed JWT rejection, strict inputs, frozen CNY pricing, idempotency, lease recovery, compensation, owner checks, cancellation, and a shared four-second HTTP deadline |
| Notification | 43 | Legacy suite with shared-events `0.1.1` |
| Shared events | 1 | Avro binary written with the previous schema reads with protocol `1` |

The test and packaging commands use the module wrappers under JDK 21. On this host, Testcontainers used `JAVA_TOOL_OPTIONS=-Dapi.version=1.44` to match Docker Engine 29.9.3. The Catalog and Order `bootJar` artifacts were the ones used below. Both checked-in OpenAPI files parsed as OpenAPI 3.1 and all local references resolved.

## Live Compose verification

The private-network runner executed three normal tests across the open and closed intake phases and two restart tests. All five passed.

- Actual Keycloak Authorization Code with S256 PKCE authenticated two readers and an administrator. Order obtained an independent client-credentials token. Cross-user requests and v1 writes in `demo-integrated` were rejected.
- Starting with empty PostgreSQL databases applied the migrations. Seeding created 13 books and two categories. Reseeding inserted zero rows and preserved edited titles, versions, and reserved stock. Separate PostgreSQL migration checks preserved an existing Catalog book, a legacy retry payload, and an existing Order at protocol `1`.
- Concurrent submissions produced one `PLACED` result and one `REJECTED` result for the last copy. The same key replayed the winner's original ID. With intake closed, the replay stayed at HTTP 202, new work received 503, and the owner cancelled the order.
- The original cancellation was republished to Kafka. Catalog advanced its consumer-group offset after processing it. The replay left a replacement hold intact, and releasing that hold restored the single copy. The component suite separately held the offset through eleven injected database failures and observed release after the database recovered.
- A PostgreSQL test lock held the Order insert after Catalog had committed its reservation. Terminating and restarting Order recovered the same submission on attempt and fence `2`. After cancellation, PostgreSQL showed exactly one placement outbox row, one cancellation outbox row, and zero reserved copies for the fixture.
- The same persisted Schema Registry returned cancellation schema ID `3` at version `1`, then registered ID `7` at version `2` with `reservationProtocol` defaulting to `1`. A third initialization found the exact ID `7`. The live cancellation carried ID `7` on the wire.

The checked-in Compose configuration passed `docker compose -f demo/compose.yaml config --quiet`. Docker Desktop interrupted the broker and Registry image pulls with layer-store errors. Live verification used the official Apache Kafka `3.8.1` distribution and Confluent Schema Registry `7.8.2` runtime on Temurin JDK 21 via a local Compose override. The database, IdP, Java applications, seeder, and verification runner used the checked-in service definitions.

See the [startup and seed commands](README.md), [restart verification](recovery.md#verify-a-restart-after-catalog-commits), [Catalog contract](../catalog/contracts/catalog-v2.yaml), and [Order contract](../order/contracts/order-v2.yaml).
