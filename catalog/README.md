# catalog — Infrastructure 集成文档

## 服务定位

`catalog` 是书籍目录领域的边界上下文，负责书籍管理与库存控制。

**对外提供：**
- REST API（书籍查询、库存预留）
- Kafka 事件（`StockReserved`、`StockReleased`）

**消费：**
- Kafka 事件（`OrderCancelled` → 释放库存）

---

## Infrastructure 集成总览

| 中间件 | 用途 | 必须 |
|---|---|---|
| PostgreSQL | 书籍/库存数据持久化（写模型） | ✅ |
| Redis | 书籍列表/详情热点缓存 | ✅ |
| Kafka + Schema Registry | 发布 Stock 事件；消费 Order 事件 | ✅ |
| Debezium Connect | Outbox Relay（库存事件可靠投递） | ✅ |
| SigNoz / OTel | Traces + Metrics + Logs | ✅ |
| ElasticSearch | ❌ 不使用 | — |

---

## PostgreSQL

### 数据库信息

| 项目 | 值 |
|---|---|
| 数据库名 | `catalog` |
| 用户名 | `catalog` |
| 密码 | `.env` 中 `CATALOG_DBPASSWORD`（不提交 Git） |
| 地址（本地） | `localhost:5432` |

数据库和用户由 `infrastructure/db/init.sql` 在容器首次启动时创建，**业务表由服务自己通过 Flyway 管理**。

### Flyway 迁移脚本

```
src/main/resources/db/migration/
├── V1__create_books_table.sql
├── V2__create_categories_table.sql
├── V3__create_outbox_event_table.sql   # Outbox 表（Debezium 读取）
└── V4__create_stock_table.sql
```

> **约定**：迁移脚本只做 DDL，不做数据初始化（测试数据用 `@Sql` 注解注入）。

### Spring 配置

```yaml
# src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/catalog
    username: catalog
    password: ${CATALOG_DBPASSWORD}
    hikari:
      maximum-pool-size: 10
      connection-timeout: 3000
  jpa:
    hibernate:
      ddl-auto: validate        # 由 Flyway 管理 DDL，JPA 仅验证
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## Redis

### 用途

| 缓存键模式 | 内容 | TTL |
|---|---|---|
| `catalog:book:{bookId}` | 书籍详情（JSON） | 30 min |
| `catalog:books:page:{hash}` | 分页查询结果 | 5 min |
| `catalog:stock:{bookId}` | 库存可用量 | 1 min（高频读，短 TTL） |

> **缺货事件驱动失效**：`StockReserved` 发布后，适配器主动 `DEL catalog:stock:{bookId}`，下次读取时重新加载。

### Spring 配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 1000ms
```

### 缓存降级策略

Redis 不可用时服务**不降级为错误**，直接穿透查询 PostgreSQL（`@Cacheable` 自动 fallback）。
Redis 为纯缓存，不存储任何业务 Source of Truth 数据。

---

## Kafka + Schema Registry

### Topic 清单

| Topic | 方向 | Key | Value Schema |
|---|---|---|---|
| `bookstore.stock.reserved` | **发布** | `bookId`（UUID） | `com.example.events.v1.StockReserved` |
| `bookstore.stock.released` | **发布** | `bookId`（UUID） | `com.example.events.v1.StockReleased` |
| `bookstore.order.cancelled` | **消费** | `orderId`（UUID） | `com.example.events.v1.OrderCancelled` |

### Consumer Group

| Consumer Group | 消费 Topic | 描述 |
|---|---|---|
| `catalog` | `bookstore.order.cancelled` | 订单取消时释放库存 |

### Spring Kafka 配置

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
    consumer:
      group-id: catalog
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      auto-offset-reset: earliest
    properties:
      schema.registry.url: http://localhost:8085
      auto.register.schemas: false   # Schema 由 shared-events/manage-kafka.sh 预注册
      specific.avro.reader: true          # 反序列化为 SpecificRecord 子类
```

> **Topic 创建由基础设施负责**：Topic 由 `shared-events/scripts/manage-kafka.sh` 在 `setup.sh` 中统一创建，**代码中不需要也不应声明 `@Bean NewTopic`**（Kafka 已设置 `auto.create.topics.enable=false`）。

---

## Debezium Connect（Outbox Relay）

catalog 通过 **Outbox 模式**保证库存事件的可靠投递（见 [ADR-005](../docs/architecture/ADR-005-outbox-pattern.md)）。

### Outbox 表

由 Flyway `V3__create_outbox_event_table.sql` 创建：

```sql
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,   -- 'Book'
    aggregate_id   UUID NOT NULL,           -- bookId
    event_type     VARCHAR(200) NOT NULL,   -- 'com.example.events.v1.StockReserved'
    payload        JSONB NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_outbox_status ON outbox_event(status) WHERE status = 'PENDING';
```

### Debezium Connector

配置文件位于 `infrastructure/debezium/connectors/catalog-outbox-connector.json`，首次启动后注册一次：

```bash
curl -X POST http://localhost:8084/connectors \
  -H "Content-Type: application/json" \
  -d @../infrastructure/debezium/connectors/catalog-outbox-connector.json
```

Debezium 读取 PostgreSQL WAL → 经 Outbox Event Router SMT 路由 → 写入对应 Kafka Topic。

---

## ElasticSearch

**catalog 不使用 ElasticSearch。** 书籍列表查询由 PostgreSQL + Redis 缓存承担，不需要全文搜索读模型。

---

## SigNoz / OpenTelemetry

### 接入方式

通过 **OTel Java Agent**（`-javaagent`）自动注入，无需代码改动：

```yaml
# JVM 启动参数（本地开发）
JAVA_TOOL_OPTIONS: >
  -javaagent:/path/to/opentelemetry-javaagent.jar

# 环境变量
OTEL_SERVICE_NAME: catalog
OTEL_EXPORTER_OTLP_ENDPOINT: http://localhost:4317
OTEL_EXPORTER_OTLP_PROTOCOL: grpc
```

### 自动埋点覆盖范围

| 信号 | 自动覆盖内容 |
|---|---|
| **Traces** | Spring MVC HTTP 请求、JDBC SQL、Kafka produce/consume、Redis 命令 |
| **Metrics** | JVM 堆/GC、HTTP 请求率/延迟、HikariCP 连接池、Kafka consumer lag |
| **Logs** | Logback 日志自动注入 `trace_id`、`span_id`（与 Trace 关联） |

### Span 命名约定

```
catalog.book.list-books
catalog.book.get-book
catalog.stock.reserve
catalog.stock.release
```

---

## Istio / Kubernetes

> 以下为 K8s 部署时的配置说明。

### 服务端口

| 端口 | 说明 |
|---|---|
| `8081` | REST API（Ingress Gateway 路由入口） |
| `8080` | Actuator（内部健康检查、Prometheus 指标，不对外暴露） |

### Helm Chart 文件（`helm/templates/`）

| 文件 | 内容 |
|---|---|
| `deployment.yaml` | 单副本（本地）/ HPA 托管（prod） |
| `service.yaml` | ClusterIP，端口 8081 |
| `hpa.yaml` | CPU > 70% 触发扩容，最大 3 副本 |
| `networkpolicy.yaml` | 放行：Ingress Gateway → 8081；Egress → PostgreSQL:5432、Redis:6379、Kafka:29092、Schema Registry:8081 |
| `virtual.yaml` | 路由到 catalog，超时 5s，重试 3 次（5xx 时） |
| `destination-rule.yaml` | 熔断器：连续 5 次 5xx 后驱逐实例，30s 恢复 |
| `configmap.yaml` | 非敏感配置（`OTEL_SERVICE_NAME` 等） |
| `serviceaccount.yaml` | 独立 ServiceAccount |

### VirtualService 路由规则

```
bookstore.local/api/v1/books*           → catalog:8081
bookstore.local/api/v1/books/{id}/stock → catalog:8081（内部服务间调用）
```

---

## 本地启动

```bash
# 1. 启动基础设施（自动完成 Topic 创建、Schema 注册、Debezium Connector 注册）
cd ../infrastructure && ./setup.sh && cd -

# 2. 确认 shared-events SDK 已发布到 mavenLocal
cd ../shared-events && ./gradlew publishToMavenLocal && cd -

# 3. 启动服务
./gradlew bootRun
```

服务启动后可访问：
- REST API：`http://localhost:8081/api/v1/books`
- 健康检查：`http://localhost:8081/actuator/health`
