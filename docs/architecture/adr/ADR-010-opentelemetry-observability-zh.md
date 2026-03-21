# ADR-010: 通过 Kubernetes Operator 使用 OpenTelemetry 实现统一可观测性

- **Status**: Accepted
- **Date**: 2026-03-11
- **Deciders**: Architecture Team
- **Reference**: [OpenTelemetry Operator for Kubernetes](https://opentelemetry.io/docs/kubernetes/operator/)

---

## 背景

CLAUDE.md 要求每个服务必须：

1. 导出 OpenTelemetry 追踪数据
2. 在 HTTP 和 Kafka 消息中传播 `traceparent` 头
3. 暴露 Prometheus 指标
4. 使用包含 trace/span ID 字段的结构化 JSON 日志

针对 Spring Boot + OTel 的集成，存在三种方案：

### 方案 A——独立 OTel Java Agent
下载 `opentelemetry-javaagent.jar`，在 JVM 启动时通过 `-javaagent` 参数传入。无需修改代码，开箱即可自动插桩 Spring MVC、Kafka、JDBC、Redis。

**问题**：agent jar 必须按服务单独管理——内嵌于 Docker 镜像或通过每个 Helm Chart 的 init container 挂载。升级 OTel 需要重新构建所有镜像或同时更新所有 Helm Chart。自定义 Span（手动插桩）无论如何都需要添加 `opentelemetry-api` 编译依赖，最终形成"agent + 依赖"的混合方案。

### 方案 B——仅使用 Spring Boot Starter 依赖
在每个服务的 `build.gradle.kts` 中添加 `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`。Spring Boot 3 通过 Micrometer Observation 自动配置追踪和指标。

**问题**：每个消费服务都必须添加 3-4 个依赖并配置 OTLP 端点。seedwork starter 无法强制执行这一点——只能通过文档说明。自动插桩覆盖范围比 agent 窄（例如 JDBC 需要额外添加 `datasource-micrometer` 依赖）。

### 方案 C——OTel Operator（Kubernetes 原生）
在集群中部署 [OpenTelemetry Operator](https://opentelemetry.io/docs/kubernetes/operator/)，定义一个 `Instrumentation` 自定义资源，指定 agent 版本、导出器端点和传播器。每个服务只需在 `Deployment` 上添加**一个注解**即可接入。

Operator 会自动以 init container 的形式注入 Java agent——无需修改 Dockerfile，无需在每个服务中管理依赖。

---

## 决策

我们采用**方案 C：OpenTelemetry Operator** 进行 agent 注入，同时在 seedwork 中使用 **Micrometer** 进行应用层指标插桩。

### 决策理由

**通过 Operator 注入 Agent** 从中心统一处理基础设施关切（OTel 如何到达 JVM），无需给各服务团队增加负担。Operator 在集群范围内统一管理 agent 版本，升级 OTel 只需更新一个 CR。

**Micrometer 继续作为 seedwork 内的指标 API**（`SpringCommandBus`、`SpringQueryBus`、outbox relay、死信计数器）。OTel Java agent 包含 Micrometer 桥接，可自动将 Micrometer 指标通过 OTLP 导出——消费服务无需额外配置。

**关联 ID 由 OTel trace ID 替代**。agent 会自动将 `trace_id` 和 `span_id` 注入到每个请求的 MDC 中，因此不再需要自定义 `CorrelationIdFilter`。W3C `traceparent` 头可透明地在 HTTP 调用和 Kafka 消息中传播。

### 基础设施配置（一次性，归属 `infrastructure/`）

**1. 安装 Operator**

```bash
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm install opentelemetry-operator open-telemetry/opentelemetry-operator \
  --namespace opentelemetry-operator-system \
  --create-namespace \
  --set "manager.collectorImage.repository=otel/opentelemetry-collector-contrib"
```

**2. Instrumentation CR**（每个集群命名空间一个，定义于 `infrastructure/helm/otel/`）

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

**3. OTel Collector 流水线**（`infrastructure/helm/otel-collector/`）

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

### 各服务配置（每个微服务，归属服务团队）

**Helm `deployment.yaml`**——添加一个注解：

```yaml
spec:
  template:
    metadata:
      annotations:
        instrumentation.opentelemetry.io/inject-java: "true"
```

**Helm `values.yaml`**——服务名（用作 `OTEL_SERVICE_NAME`）：

```yaml
# The Operator reads spring.application.name or OTEL_SERVICE_NAME automatically
# when the Instrumentation CR sets exporter.endpoint. No extra env vars needed
# if OTEL_SERVICE_NAME is set via the Helm chart's existing env block.
```

**`application.yml`**——无需任何 OTel 专属配置，agent 自动处理一切。

### seedwork 职责

seedwork **不**添加 OTel 依赖，其职责保持不变：

| 关切点 | 实现方式 |
|---|---|
| 命令/查询指标 | `SpringCommandBus` / `SpringQueryBus` 中的 `MeterRegistry` |
| Outbox 未发布计量 | `OutboxAutoConfiguration` 中的 `MeterRegistry` |
| 死信计数器 | `RetryEntryProcessor` 中的 `MeterRegistry` |
| 死信通知 | `ApplicationEventPublisher`——服务订阅 `DeadLetteredEvent` |

OTel agent 在运行时通过桥接自动将 Micrometer 指标导出至 OTLP。

### 信号流向

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

## 影响

### 积极影响

- **消费服务零代码变更**：接入只需在 Deployment 上添加一个注解。
- **集中式 agent 版本管理**：升级 OTel 只需更新一个 `Instrumentation` CR，无需重新构建任何服务。
- **完整自动插桩覆盖**：Spring MVC、Kafka、JDBC、Redis、HTTP 客户端——无需任何代码即可全部插桩。
- **自动关联 ID**：`trace_id` / `span_id` 由 agent 注入 MDC，无需 `CorrelationIdFilter`。
- **W3C `traceparent` 传播**：HTTP 和 Kafka 头在服务边界之间透明传播。
- **统一信号流水线**：追踪、指标和日志均流经同一个 OTel Collector，简化后端配置。
- **Micrometer 兼容性**：seedwork 中现有的 Micrometer 指标通过 agent 桥接导出，无需任何代码变更。

### 消极影响

- **Kubernetes 依赖**：Operator 方案仅在 Kubernetes 中有效。在没有 OTel Operator 的本地开发环境中，需要在每个服务的运行配置中手动设置 `JAVA_TOOL_OPTIONS=-javaagent:/path/to/opentelemetry-javaagent.jar`。
- **Operator 运维开销**：OTel Operator 是需要维护、监控和升级的额外集群组件。
- **init container 启动耗时**：agent 注入会在 Pod 启动时增加一个小的 init container 步骤。
- **采样配置为集群级别**：`Instrumentation` CR 适用于命名空间内的所有服务。若需要按服务设置采样率，需要独立的 CR 或在各 Helm Chart 中覆盖环境变量。

### 本地开发缓解措施

在没有 OTel Operator 的情况下本地运行服务时，在 IDE 或运行配置中设置以下环境变量：

```
JAVA_TOOL_OPTIONS=-javaagent:/path/to/opentelemetry-javaagent.jar
OTEL_SERVICE_NAME=catalog
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```
