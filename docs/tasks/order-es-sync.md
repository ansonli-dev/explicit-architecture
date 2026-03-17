# Task: Order ES Read Model Sync (CQRS Projection)

## 背景

Order 服务采用 CQRS 模式：写端用 PostgreSQL，读端用 ElasticSearch。
本任务实现"写操作 → ES 读模型同步"的完整链路：

```
PlaceOrderCommand
    → Order.place() 注册 OrderPlaced 域事件
        → OrderPersistenceAdapter.save() 触发 OutboxWriteListener
            → outbox_event 表（同一事务）
                → Debezium CDC → Kafka topic
                    → OrderPlacedConsumer → ElasticSearch
```

---

## 任务列表

### TASK-001 ✅ 完成
**扩展 `OrderSearchRepository` 端口，增加写端方法**

- 文件：`application/port/outbound/OrderSearchRepository.java`
- 新增 `OrderProjection` record（避免暴露 ES 类型到端口）
- 新增 `save(OrderProjection)`、`updateStatus()`、`updateStatusWithTracking()`、`updateStatusWithReason()`

---

### TASK-002 ✅ 完成
**实现 `OrderSearchAdapter` 写端方法**

- 文件：`infrastructure/repository/elasticsearch/OrderSearchAdapter.java`
- 实现 TASK-001 中新增的四个端口方法

---

### TASK-003 ✅ 完成
**完善 `OrderOutboxMapper`：补充 OrderConfirmed / OrderShipped**

- 文件：`infrastructure/messaging/outbox/OrderOutboxMapper.java`
- 当前只映射 `OrderPlaced` + `OrderCancelled`，补充另外两个

---

### TASK-004 ✅ 完成
**`application.yml` 添加 Avro specific reader 配置**

- 文件：`src/main/resources/application.yml`
- 新增 `spring.kafka.properties.specific.avro.reader: "true"`
- 确保 KafkaAvroDeserializer 返回具体 Avro 类型而非 GenericRecord

---

### TASK-005 ✅ 完成
**实现 `OrderPlacedConsumer`**

- 文件：`interfaces/kafka/OrderPlacedConsumer.java`
- 消费 `bookstore.order.placed`，在 ES 中创建订单文档
- 使用 `KafkaMessageProcessor` + `RetryableKafkaHandler<OrderPlaced>` 确保幂等+重试

---

### TASK-006 ✅ 完成
**实现 `OrderCancelledConsumer`**

- 文件：`interfaces/kafka/OrderCancelledConsumer.java`
- 消费 `bookstore.order.cancelled`，更新 ES 订单状态 → Cancelled

---

### TASK-007 ✅ 完成
**实现 `OrderConfirmedConsumer`**

- 文件：`interfaces/kafka/OrderConfirmedConsumer.java`
- 消费 `bookstore.order.confirmed`，更新 ES 订单状态 → Confirmed

---

### TASK-008 ✅ 完成
**实现 `OrderShippedConsumer`**

- 文件：`interfaces/kafka/OrderShippedConsumer.java`
- 消费 `bookstore.order.shipped`，更新 ES 订单状态 → Shipped + trackingNumber

---

### TASK-009 ✅ 完成
**创建 Kafka Topics**

```bash
bash infrastructure/scripts/init-kafka.sh
```

已创建 6 个 Topics + SASL ACL 配置完毕。

---

### TASK-010 ✅ 完成（连接器配置已修复，需服务部署后重新注册）
**注册 Debezium Connectors**

> ⚠️ 依赖：`outbox_event` 表由 Flyway 在服务启动时创建。
> 服务首次部署后，需重新执行注册脚本：
>
> ```bash
> kubectl port-forward -n infra svc/debezium 8084:8083
> bash infrastructure/debezium/register-connectors.sh
> ```

已修复的问题：
- 密码占位符替换为实际值
- 列名更新：`event_id`, `message_key`, `topic`, `avro_payload`（匹配 V3 schema）
- `publication.autocreate.mode: filtered`（无需超级用户）
- `value.converter` 改为 `ByteArrayConverter`（匹配 Avro binary payload）

数据库准备（已手动执行）：
- 创建 `order`, `notification` 用户和数据库
- 授予 `REPLICATION` 属性给 `order`, `catalog` 用户

> **注意**：`OutboxRelayScheduler`（seedwork 轮询机制）是当前主要的 outbox relay，
> 与 Debezium CDC 只需选其一。两者同时运行会导致 Kafka 重复消息。

---

## 数据流验证

完成所有任务后，端到端验证：

```bash
# 1. 下单
curl -X POST http://localhost:8082/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"...", "customerEmail":"test@test.com", "items":[...]}'

# 2. 检查 ES（等待约5秒 outbox relay 间隔）
curl http://localhost:8082/api/v1/orders/{orderId}
```
