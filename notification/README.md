# notification — Infrastructure 集成文档

## 服务定位

`notification` 是通知领域的边界上下文，**纯事件驱动**，无对外 REST 写入入口。监听订单事件，生成并记录通知（Demo 阶段通过日志模拟发送邮件，无真实 SMTP）。

**消费：**
- Kafka 事件（`OrderPlaced`、`OrderConfirmed`、`OrderCancelled`、`OrderShipped`）

**对外提供：**
- REST API（查询通知记录，只读）

---

## Infrastructure 集成总览

| 中间件 | 用途 | 必须 |
|---|---|---|
| PostgreSQL | 通知记录持久化 | ✅ |
| Kafka + Schema Registry | 消费 Order 事件 | ✅ |
| SigNoz / OTel | Traces + Metrics + Logs | ✅ |
| Redis | ❌ 不使用 | — |
| Debezium Connect | ❌ 不使用（无 Outbox，不发布跨服务事件） | — |
| ElasticSearch | ❌ 不使用 | — |

> **为什么不需要 Outbox？** notification **只消费**事件，不跨服务发布事件，因此不需要 Outbox 模式。通知记录只走 PostgreSQL 即可。

---

## PostgreSQL

### 数据库信息

| 项目 | 值 |
|---|---|
| 数据库名 | `notification` |
| 用户名 | `notification` |
| 密码 | `.env` 中 `NOTIFICATION_DBPASSWORD` |
| 地址（本地） | `localhost:5432` |

### Flyway 迁移脚本

```
src/main/resources/db/migration/
├── V1__create_notifications_table.sql
└── V2__create_processed_events_table.sql  # 消费幂等去重表
```

### 幂等去重表

notification 作为 Kafka 消费者，必须处理**至少一次投递**（at-least-once）的重复消费问题（见 [ADR-005](../docs/architecture/ADR-005-outbox-pattern.md)）：

```sql
-- V2__create_processed_events_table.sql
CREATE TABLE processed_events (
    event_id    UUID PRIMARY KEY,           -- 来自 Avro 消息 eventId 字段
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

消费者在处理前先查 `processed_events`，已处理则幂等跳过：

```java
if (processedEventRepository.existsById(event.getEventId())) {
    log.debug("Duplicate event {}, skipping", event.getEventId());
    return;
}
// 处理业务逻辑...
// 同一事务中插入 processed_events
```

### Spring 配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/notification
    username: notification
    password: ${NOTIFICATION_DBPASSWORD}
    hikari:
      maximum-pool-size: 5              # 通知服务并发较低
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## Kafka + Schema Registry

### 消费 Topic 清单

| Topic | 触发动作 | 通知内容 |
|---|---|---|
| `bookstore.order.placed` | 创建"订单已收到"通知 | 发送订单确认邮件（日志模拟） |
| `bookstore.order.confirmed` | 创建"订单已确认"通知 | 通知用户开始备货 |
| `bookstore.order.cancelled` | 创建"订单已取消"通知 | 告知用户取消原因 |
| `bookstore.order.shipped` | 创建"订单已发货"通知 | 包含快递单号 |

### Consumer Group

| Consumer Group | 消费 Topic | 说明 |
|---|---|---|
| `notification` | `bookstore.order.*`（全部 4 个） | 一个 Consumer Group 订阅所有 Order 事件 |

### Spring Kafka 配置

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: notification
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      auto-offset-reset: earliest
      # 手动提交 Offset（保证处理完成后才提交，避免消息丢失）
      enable-auto-commit: false
    listener:
      ack-mode: MANUAL_IMMEDIATE
    properties:
      schema.registry.url: http://localhost:8085
      auto.register.schemas: false   # Schema 由 shared-events/manage-kafka.sh 预注册
      specific.avro.reader: true
```

> **为什么手动提交 Offset？** 自动提交可能在 DB 写入失败后已提交 Offset，导致消息丢失。手动提交保证"处理成功 + DB 写入 + Offset 提交"的顺序正确性。

### 无需声明 Topic

notification **不创建**任何 Topic，只消费事件。Topic 统一由 `shared-events/scripts/manage-kafka.sh` 预创建（`setup.sh` 自动执行）。

---

## Debezium Connect

**notification 不使用 Debezium。**

此服务只消费事件，不向其他服务发布事件，不需要 Outbox 模式，因此无 Debezium Connector。

---

## 邮件发送（Demo 模拟）

Demo 阶段不使用真实 SMTP，邮件发送通过日志模拟：

```java
// infrastructure/email/LogEmailAdapter.java
@Component
@ConditionalOnProperty(name = "notification.email.log-only", havingValue = "true")
public class LogEmailAdapter implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LogEmailAdapter.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[EMAIL SIMULATION] To={}, Subject={}, Body={}", to, subject, body);
    }
}
```

```yaml
# application.yml
notification:
  email:
    log-only: true   # true = 日志模拟；false = 真实 SMTP（仅 prod 使用）
```

---

## SigNoz / OpenTelemetry

```yaml
OTEL_SERVICE_NAME: notification
OTEL_EXPORTER_OTLP_ENDPOINT: http://localhost:4317
OTEL_EXPORTER_OTLP_PROTOCOL: grpc
```

### 自动埋点覆盖范围

| 信号 | 自动覆盖内容 |
|---|---|
| **Traces** | Kafka 消费（含 `traceparent` 传播，与 order 的 Trace 链路连通）、JDBC SQL |
| **Metrics** | JVM 堆/GC、Kafka consumer lag（监控通知是否积压）、HikariCP |
| **Logs** | 注入 `trace_id`、`span_id` |

### Trace 传播

Debezium 发布的 Kafka 消息头中包含 `traceparent`，notification 的 OTel Agent 自动提取并创建**子 Span**，使得整条链路可追溯：

```
Customer → order (HTTP) → PostgreSQL (Outbox) → Debezium → Kafka
  → notification (Kafka Consumer) → PostgreSQL (通知记录)
                                          → LogEmailAdapter
```

SigNoz 中可查看跨服务的完整 Trace。

### Span 命名约定

```
notification.notification.handle-order-placed
notification.notification.handle-order-confirmed
notification.notification.handle-order-cancelled
notification.notification.handle-order-shipped
notification.notification.get-notifications
```

---

## Istio / Kubernetes

### 服务端口

| 端口 | 说明 |
|---|---|
| `8083` | REST API（只读：查询通知记录） |
| `8080` | Actuator（内部） |

### Helm Chart 文件（`helm/templates/`）

| 文件 | 内容 |
|---|---|
| `deployment.yaml` | 单副本（通知服务无状态，可按 Kafka Consumer Group 语义扩容） |
| `service.yaml` | ClusterIP，端口 8083 |
| `hpa.yaml` | CPU > 70% 触发扩容，最大 3 副本（多实例时 Kafka 自动分配 Partition） |
| `networkpolicy.yaml` | 放行：Ingress Gateway → 8083；Egress → PostgreSQL:5432、Kafka:29092、Schema Registry:8081 |
| `virtual.yaml` | 路由到 notification，超时 5s |
| `destination-rule.yaml` | 熔断器配置 |
| `configmap.yaml` | 含 `NOTIFICATION_EMAIL_LOG_ONLY=true` |
| `serviceaccount.yaml` | 独立 ServiceAccount |

### VirtualService 路由规则

```
bookstore.local/api/v1/notifications*  → notification:8083（只读查询）
```

---

## 本地启动

```bash
# 1. 启动基础设施（自动完成 Topic 创建、Schema 注册）
cd ../infrastructure && ./setup.sh && cd -

# 2. 确认 shared-events SDK 已发布
cd ../shared-events && ./gradlew publishToMavenLocal && cd -

# 3. 启动服务
./gradlew bootRun
```

> **启动顺序依赖**：order（及其 Debezium Connector）需先启动并成功发布消息，notification 才会有数据可消费。

服务启动后可访问：
- 查询通知：`GET http://localhost:8083/api/v1/notifications?customerId={id}`
- 健康检查：`http://localhost:8083/actuator/health`
