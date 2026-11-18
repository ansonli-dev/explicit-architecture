# Bookstore checkout verification for issue #3

Verified on 2026-11-18 with the release frontend, Java Catalog and Order v2, Keycloak, PostgreSQL, Kafka, and Schema Registry. The frontend contract snapshots record the verified backend commit.

- Catalog passed all 87 tests and Order passed all 111 tests under JDK 21. A PostgreSQL migration probe recovered historical times from the outbox, preserved a missing historical timestamp, and verified the timestamp on a new order.
- The live REST tests verified quote reads and expiry, both submission replay statuses, missing books, changed prices, original purchased line snapshots, and the race for the last copy. With intake closed, new work received 503 and existing submissions replayed. The same cancellation returned once with 201 and then with 200. Kafka replay left a replacement stock hold intact.
- With Catalog stopped, a real submission reached `RECONCILE_REQUIRED` through the normal retry budget. Its owner could still read and replay it, another reader received 404, and no order appeared. Catalog was restarted after the test.
- Chromium passed all three [live paths](README.md#browser-verification). It signed in through Keycloak, reloaded checkout and order history, retried after a lost submission response, and verified that the losing reader retained their cart. Cleanup restored the last copy to one. Two catalog reseed runs inserted or changed zero rows.
- The frontend passed the production and SQLite demo builds, `pnpm check`, `pnpm api:check`, and all 113 Vitest tests. The separate macOS Chromium regression run passed 53 cases, skipped 15, and failed the keyboard category selection in both demo and release. The same two failures reproduced at the original frontend commit `4996982`.

The Compose configuration validated with the checked-in image references. Docker Desktop returned layer-store lease errors while pulling the Confluent images. The live run used the Confluent Kafka `7.8.2-ccs` and Schema Registry `7.8.2` runtimes on Temurin 21 with a local Compose override. The project used the checked-in topics, Avro schemas, databases, Keycloak realm, Java jars, and frontend image.

The [captured Order v2 responses](contracts/order-v2-responses.json) are from these synthetic test runs. They contain HTTP status, the resource location when present, and the response body. They contain no tokens, idempotency keys, or customer claims.
