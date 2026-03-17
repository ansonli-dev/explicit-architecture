# ADR-010: OpenTelemetry via Kubernetes Operator for Unified Observability

- **Status**: Accepted
- **Date**: 2026-03-11
- **Deciders**: Architecture Team
- **Reference**: [OpenTelemetry Operator for Kubernetes](https://opentelemetry.io/docs/kubernetes/operator/)

---

## Context

CLAUDE.md requires every service to:

1. Export OpenTelemetry traces
2. Propagate `traceparent` header across HTTP and Kafka messages
3. Expose Prometheus metrics
4. Log structured JSON with trace/span ID fields

Three integration approaches exist for Spring Boot + OTel:

### Option A — OTel Java Agent (standalone)
Download `opentelemetry-javaagent.jar` and pass `-javaagent` at JVM startup. Zero code changes; auto-instruments Spring MVC, Kafka, JDBC, Redis out of the box.

**Problem**: the agent jar must be managed per service — embedded in the Docker image or mounted via an init container in every Helm chart. Upgrading OTel requires rebuilding all images or updating all Helm charts simultaneously. Custom spans (manual instrumentation) require adding `opentelemetry-api` as a compile dependency anyway, creating a hybrid "agent + dependency" setup.

### Option B — Spring Boot Starter dependencies only
Add `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` to each service's `build.gradle.kts`. Spring Boot 3 auto-configures traces and metrics via Micrometer Observation.

**Problem**: every consuming service must add 3–4 dependencies and configure the OTLP endpoint. The seedwork starter cannot enforce this — it can only document it. Auto-instrumentation coverage is narrower than the agent (e.g., JDBC requires an additional `datasource-micrometer` dependency).

### Option C — OTel Operator (Kubernetes-native)
Deploy the [OpenTelemetry Operator](https://opentelemetry.io/docs/kubernetes/operator/) to the cluster. Define a single `Instrumentation` custom resource that specifies the agent version, exporter endpoint, and propagators. Each service opts in with **one annotation** on its `Deployment`.

The Operator injects the Java agent as an init container automatically — no Dockerfile changes, no per-service dependency management.

---

## Decision

We adopt **Option C: OpenTelemetry Operator** for agent injection, combined with **Micrometer** for application-level metrics instrumentation in seedwork.

### Rationale

**Agent injection via Operator** handles the infrastructure concern (how OTel reaches the JVM) centrally, without burdening individual service teams. The Operator manages agent versioning cluster-wide; upgrading OTel is a single CR update.

**Micrometer stays as the metrics API** inside seedwork (`SpringCommandBus`, `SpringQueryBus`, outbox relay, dead-letter counter). The OTel Java agent includes a Micrometer bridge that automatically exports Micrometer meters via OTLP — no additional wiring needed in consuming services.

**Correlation ID is replaced by OTel trace ID**. The agent automatically injects `trace_id` and `span_id` into MDC for every request, making a custom `CorrelationIdFilter` unnecessary. The W3C `traceparent` header propagates across HTTP calls and Kafka messages transparently.

### Infrastructure Setup (one-time, owned by `infrastructure/`)

**1. Install the Operator**

```bash
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm install opentelemetry-operator open-telemetry/opentelemetry-operator \
  --namespace opentelemetry-operator-system \
  --create-namespace \
  --set "manager.collectorImage.repository=otel/opentelemetry-collector-contrib"
```

**2. Instrumentation CR** (one per cluster namespace, defined in `infrastructure/helm/otel/`)

```yaml
apiVersion: opentelemetry.io/v1alpha1
kind: Instrumentation
metadata:
  name: bookstore-instrumentation
  namespace: bookstore
spec:
  exporter:
    endpoint: http://otel-collector:4317
  propagators:
    - tracecontext   # W3C traceparent — cross-service trace propagation
    - baggage
  sampler:
    type: parentbased_traceidratio
    argument: "1.0"  # reduce in production (e.g. "0.1")
  java:
    image: ghcr.io/open-telemetry/opentelemetry-operator/autoinstrumentation-java:2.9.0
    env:
      - name: OTEL_LOGS_EXPORTER
        value: otlp
      - name: OTEL_METRICS_EXPORTER
        value: otlp
      - name: OTEL_TRACES_EXPORTER
        value: otlp
```

**3. OTel Collector pipeline** (`infrastructure/helm/otel-collector/`)

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
processors:
  batch:
  memory_limiter:
    limit_mib: 512
exporters:
  prometheus:
    endpoint: 0.0.0.0:8889
  otlp/jaeger:
    endpoint: jaeger:4317
  loki:
    endpoint: http://loki:3100/loki/api/v1/push
service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [otlp/jaeger]
    metrics:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [prometheus]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [loki]
```

### Per-Service Setup (each microservice, owned by service teams)

**Helm `deployment.yaml`** — add one annotation:

```yaml
spec:
  template:
    metadata:
      annotations:
        instrumentation.opentelemetry.io/inject-java: "true"
```

**Helm `values.yaml`** — service name (used as `OTEL_SERVICE_NAME`):

```yaml
# The Operator reads spring.application.name or OTEL_SERVICE_NAME automatically
# when the Instrumentation CR sets exporter.endpoint. No extra env vars needed
# if OTEL_SERVICE_NAME is set via the Helm chart's existing env block.
```

**`application.yml`** — no OTel-specific config required. The agent handles everything.

### seedwork Responsibilities

seedwork does **not** add OTel dependencies. Its responsibilities remain:

| Concern | Implementation |
|---|---|
| Command/Query metrics | `MeterRegistry` in `SpringCommandBus` / `SpringQueryBus` |
| Outbox unpublished gauge | `MeterRegistry` in `OutboxAutoConfiguration` |
| Dead-letter counter | `MeterRegistry` in `RetryEntryProcessor` |
| Dead-letter notification | `ApplicationEventPublisher` — services subscribe to `DeadLetteredEvent` |

The OTel agent bridges Micrometer meters to OTLP automatically at runtime.

### Signal Flow

```
Spring MVC / Kafka / JDBC / Redis
        │  (auto-instrumented by OTel Java agent)
        ▼
  OTel Java Agent (injected by Operator)
        │  traces + metrics + logs via OTLP/gRPC
        ▼
  OTel Collector  ──► Jaeger   (traces)
                  ──► Prometheus (metrics, scraped from Collector)
                  ──► Loki     (logs with trace_id field)

Micrometer meters (seedwork埋点)
        │  (bridged by agent's Micrometer bridge)
        ▼
  OTel Collector ──► Prometheus
```

---

## Consequences

### Positive

- **Zero consuming-service code changes**: opt-in is a single Deployment annotation.
- **Centralized agent versioning**: upgrading OTel is a single `Instrumentation` CR update, not a rebuild of every service.
- **Full auto-instrumentation coverage**: Spring MVC, Kafka, JDBC, Redis, HTTP clients — all instrumented without any code.
- **Automatic correlation ID**: `trace_id` / `span_id` injected into MDC by the agent; no `CorrelationIdFilter` needed.
- **W3C `traceparent` propagation**: HTTP and Kafka headers handled transparently across service boundaries.
- **Unified signal pipeline**: traces, metrics, and logs all flow through one OTel Collector, simplifying backend configuration.
- **Micrometer compatibility**: existing Micrometer meters in seedwork are exported via the agent's bridge without any code changes.

### Negative

- **Kubernetes dependency**: the Operator approach only works in Kubernetes. Local development without the OTel Operator requires manually setting `JAVA_TOOL_OPTIONS=-javaagent:/path/to/opentelemetry-javaagent.jar` in each service's run configuration.
- **Operator operational overhead**: the OTel Operator is an additional cluster component to maintain, monitor, and upgrade.
- **Init container startup cost**: agent injection adds a small init container step to pod startup time.
- **Sampling configuration is cluster-wide**: the `Instrumentation` CR applies to all services in the namespace. Per-service sampling rates require separate CRs or environment variable overrides in individual Helm charts.

### Local Development Mitigation

When running a service locally without the OTel Operator, set these environment variables in the IDE or run configuration:

```
JAVA_TOOL_OPTIONS=-javaagent:/path/to/opentelemetry-javaagent.jar
OTEL_SERVICE_NAME=catalog
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```
