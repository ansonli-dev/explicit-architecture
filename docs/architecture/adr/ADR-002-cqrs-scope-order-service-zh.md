# ADR-002: 仅在 order 服务中应用 CQRS

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## 背景

CQRS（命令查询职责分离）将写模型与读模型分离。它引入了相当高的复杂性：两套存储系统、事件驱动的读模型投影、查询侧的最终一致性，以及额外的基础设施运维负担。

我们需要决定：
1. 哪些服务从 CQRS 获得的收益足以抵消其开销？
2. 采用何种形式的 CQRS（同一数据库使用独立模型，还是独立存储）？

### 各服务特征分析

| 服务 | 写操作特征 | 读操作特征 | 结论 |
|---|---|---|---|
| `catalog` | 低频管理操作（新增/更新书籍） | 高频读取（浏览、搜索） | Redis 缓存已足够；CQRS 开销不合算 |
| `order` | 事务性订单生命周期；写侧强一致性 | 复杂查询：按客户、日期范围、状态、全文搜索；历史视图 | **CQRS 合理** |
| `notification` | 仅追加（追加日志） | 按客户 ID 简单读取 | 过于简单，不适合 CQRS |

---

## 决策

**CQRS 仅应用于 `order` 服务**，使用物理分离的存储：

- **写侧**：PostgreSQL——事务性、强一致性、规范化 schema。命令（`PlaceOrderCommand`、`CancelOrderCommand`）由专用 `CommandHandler` 类（`PlaceOrderCommandHandler`、`CancelOrderCommandHandler`）处理，这些类变更 `Order` 聚合并通过 `OrderPersistence`（JPA）持久化。
- **读侧**：ElasticSearch——非规范化，针对搜索优化。查询（`FindOrdersByCustomerQuery`）由专用 `QueryHandler` 类（`GetOrderQueryHandler`、`ListOrdersQueryHandler`）处理，这些类直接访问 `OrderSearchRepository`（ElasticSearch 适配器），完全绕过领域层。

### 投影机制

当 `CommandHandler` 持久化命令时，它同时通过 Outbox 模式（参见 ADR-005）将领域事件（如 `OrderPlaced`、`OrderCancelled`）发布到 Kafka。`order` 服务内的一个 Kafka 消费者（**投影器**）消费这些事件并更新 ElasticSearch 读模型。

```
[Command] → PlaceOrderCommandHandler → PostgreSQL（写）
                                     → Outbox → Kafka
                                                  ↓
                                          OrderPlacedConsumer（投影器）
                                                  ↓
                                          ElasticSearch（读）

[Query]   → GetOrderQueryHandler / ListOrdersQueryHandler → ElasticSearch
```

### CQRS 在此处不意味着什么

- 写侧**不**使用事件溯源。PostgreSQL 存储聚合的当前状态，而非事件日志。
- 命令总线**不是**独立进程——它是同一 JVM 内的方法调用。
- `catalog` 和 `notification` 使用**简单的 CRUD 方式**，采用单一数据存储。

---

## 影响

### 积极影响
- `order` 的读查询（客户历史、全文搜索、分面过滤）由 ElasticSearch 提供服务，不会对事务性 PostgreSQL 造成压力。
- 写路径保持强一致性和 ACID 合规性。
- 分离在包结构中清晰可见（`application/command/` 和 `application/query/` 是独立的子树；`infrastructure/search/` 存放 ES 适配器）。

### 消极影响
- **读侧最终一致性**：订单下单后，经过短暂延迟（毫秒到秒级）才会出现在 ElasticSearch 查询结果中。
- **额外基础设施**：ElasticSearch 必须与 PostgreSQL 一同运维。
- **投影维护**：如果 ElasticSearch 索引 schema 发生变化，重建索引任务必须重放所有事件或重新读取 PostgreSQL。

### 超出范围
- 本项目**不**采用完整的事件溯源（将事件作为主要事实来源）。如果审计追踪或时态查询需求出现，可重新审视此决策。
