---
type: operations
title: Runtime configuration and Helm deployment
description: Explains independent builds, service configuration, infrastructure prerequisites, Helm connector hooks, and the limits of checked-in observability and mesh settings.
tags: [operations, configuration, helm, deployment, observability]
verified:
  - by: openwiki/0.5.2
    at: 2026-09-16T08:14:48.980Z
sources:
  - id: openwiki-source-342594298fb62993a735eefa
    resource: repo://catalog/src/main/resources/application.yml
  - id: openwiki-source-74c57bc7db7612e8ad15fcbf
    resource: repo://notification/src/main/resources/application.yml
  - id: openwiki-source-895325b430ff49b88beb7112
    resource: repo://order/build.gradle.kts
  - id: openwiki-source-bcb277da804158bdbcb9b977
    resource: repo://order/helm/templates/debezium-connector-job.yaml
  - id: openwiki-source-59b089d8288fc143ee753b0d
    resource: repo://order/helm/templates/deployment.yaml
  - id: openwiki-source-08fd646658da82eda43f9ea3
    resource: repo://order/helm/templates/es-sink-connector-job.yaml
  - id: openwiki-source-40e0478cc5d9a5707f3e7499
    resource: repo://order/helm/templates/networkpolicy.yaml
  - id: openwiki-source-b77d400d60c463e3b5289b4f
    resource: repo://order/helm/templates/orders-cdc-connector-job.yaml
  - id: openwiki-source-6b54a13803f48a21a555720e
    resource: repo://order/helm/templates/virtual-service.yaml
  - id: openwiki-source-1ee456911cc976ffda4284be
    resource: repo://order/helm/values.yaml
  - id: openwiki-source-2eb2fbcfde7c4960520b7050
    resource: repo://order/settings.gradle.kts
  - id: openwiki-source-97d8079467d29be6bb98070b
    resource: repo://order/src/main/java/com/example/order/infrastructure/client/CatalogClientConfig.java
  - id: openwiki-source-b00d4312e38883b0c3231809
    resource: repo://order/src/main/java/com/example/order/interfaces/rest/OrderCommandController.java
  - id: openwiki-source-85f28f64471f782c274d0aa6
    resource: repo://order/src/main/resources/application.yml
generated: { by: "codex", at: "2026-09-16T08:14:48.980Z" }
---

# Runtime configuration and Helm deployment

The repository provides service builds and Helm charts, with infrastructure endpoints expected to be supplied by the environment. These files describe deployment inputs; their presence does not establish that the services, brokers, connectors, or monitoring are running. Start with [Quickstart](../quickstart.md) for local build commands.

## Build artifacts

Each service is an independent Gradle project using Java 21 and preview-enabled compilation and execution. The checked-in Order version catalog pins Spring Boot 3.3.5. Service builds depend on published `com.example:seedwork:0.1.0` and `com.example:shared-events:0.1.0`; their local-development route is `mavenLocal()`, with GitHub Packages, Confluent Maven, and Maven Central also configured.

The Jib service builds use a local-Docker base-image reference, `docker://eclipse-temurin:21-jre-alpine`, and container flags including `--enable-preview` and ZGC. `jibDockerBuild` depends on main-class resolution. Building a container therefore has prerequisites beyond compiling Java; there is no need to build or deploy images merely to inspect this wiki.

## Service settings

| Setting | Catalog | Order | Notification |
|---|---|---|---|
| HTTP port | 8081 | 8082 | 8083 |
| Default database URL suffix | `/catalog` | `/order` | `/notification` |
| Extra store | Redis at localhost:6379 | Elasticsearch at localhost:9200 | None configured |
| Kafka group default | `catalog.order-events` | `order.read-model` | `notification.order-events` |
| Outbox strategy | Explicit `scheduler` | Explicit `debezium` | Uses seedwork default if configuration applies |
| Profile/default adapter | Normal service configuration | Normal service configuration | `stub` profile; log-only email setting true |

All services enable virtual threads, disable Open Session in View, validate the JPA schema, and run Flyway from both shared and service migration locations. Kafka consumers disable auto-commit, use manual-immediate acknowledgment and specific Avro readers. Schema Registry defaults to localhost:8085, distinct from the cluster address used in Helm values.

Principal overrides are `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, and `SCHEMA_REGISTRY_URL`. Catalog adds `SPRING_DATA_REDIS_HOST/PORT`; Order adds `SPRING_ELASTICSEARCH_URIS`.

For Order's actual Catalog client, use `CATALOG_URL` or `services.catalog.base-url`. `CatalogClientConfig` reads that property when constructing the REST proxy. The separate `catalog.service.url`/`CATALOG_SERVICE_URL` setting exists in YAML but is not the property read by this client. The client code configures a base URL without its own explicit timeout or retry policy.

## Helm and external infrastructure

The Order deployment loads a ConfigMap generated from `.Values.env` and a Secret via `envFrom`. Checksum annotations cause pod-template changes when either configuration changes. Values default to a local `latest` image with pull policy `Never`, a ClusterIP on 8082, and an enabled CPU-based HPA ranging from one to five replicas. These are development-oriented checked-in inputs, not environment-independent defaults to assume elsewhere.

Startup/liveness probes target `/actuator/health/liveness`; readiness targets `/actuator/health/readiness`. Order's chart also registers three post-install/post-upgrade connector jobs, in hook-weight order:

1. Outbox relay connector, weight 5.
2. Orders table CDC connector, weight 6.
3. Elasticsearch sink connector, weight 7.

The jobs wait for the Connect REST endpoint and then update or create connector configurations. Their templates use fixed `infra` DNS endpoints and require the relevant connector plugins, reachable databases, and suitable replication configuration in that environment. The shared-events chart separately expects schema and topic ConfigMaps alongside its scripts. See [Event contracts](../integrations/event-contracts.md) before assuming schema registration will publish an updated version.

## Mesh and observability boundaries

Order's Istio VirtualService binds to `infra/platform-gateway` and `mesh`. The first route gives POST/DELETE order paths a 10-second timeout and two retries; the following prefix route uses five seconds and three retries. Cancellation is PUT, so it matches the latter rule despite the surrounding read/write comments. These retry settings do not add application-level request idempotency.

Actuator exposes health and Prometheus endpoints, and seedwork records bus duration and outbox metrics. The Order deployment explicitly disables OTel Java-agent injection. Although an OTel dependency alias and YAML settings exist, the service build's dependency list does not activate that alias; those declarations alone do not prove trace export.

The Order NetworkPolicy's monitoring ingress allows port 8080, while its configured application/service port is 8082 and no separate management port is set in application YAML. Treat actual scrape reachability as something to verify in the environment. The outbox unpublished gauge also needs strategy-aware interpretation because Debezium does not set the scheduler's publication flag.

## Focused source routes

Inspect [Order values](../../order/helm/values.yaml), [deployment](../../order/helm/templates/deployment.yaml), [CatalogClientConfig](../../order/src/main/java/com/example/order/infrastructure/client/CatalogClientConfig.java), and [application configuration](../../order/src/main/resources/application.yml). For missing order results, follow [Order read model](../integrations/order-read-model.md); for missing downstream effects, follow [Event delivery](../integrations/event-delivery.md). The [verification map](../testing/verification-map.md) identifies which tests need infrastructure and which only need Java dependencies.
