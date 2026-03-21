# ADR-006: 每服务独立数据库，禁止共享表

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## 背景

在微服务架构中，数据库归属是一个基础设计问题。可选方案如下：

| 模式 | 描述 |
|---|---|
| 共享数据库 | 所有服务共用同一个 PostgreSQL 实例和 schema |
| 每服务独立数据库 | 每个服务拥有自己的 schema 或实例 |
| 共享实例、逻辑隔离 | 单一实例，每个服务使用独立 schema |

在线书店包含三个服务，各自的数据需求明显不同：
- `catalog`：商品目录、作者数据、库存数量
- `order`：订单生命周期、订单项、支付状态
- `notification`：通知日志、投递状态、客户偏好

---

## 决策

每个微服务拥有**独立的 PostgreSQL 数据库**（逻辑上相互隔离；开发环境中可共用同一物理实例，但生产环境必须使用独立实例）。

### 规则

1. **禁止跨服务 SQL 查询**：`order` 不得对 `catalog` 的表执行 `JOIN`。
2. **禁止共享表**：任何表只能由一个服务写入。
3. **允许数据冗余**：`order` 在下单时保存书名和价格的快照，查询时不再回查 `catalog`。
4. **跨服务数据需求通过以下方式满足**：(a) 写入时发起同步 REST 调用，或 (b) 通过领域事件的事件驱动投影获取数据。
5. **schema 迁移各自独立**：每个服务的 Flyway 只管理自己的数据库 schema，没有共享迁移工具。

### 数据库分配

| 服务 | 数据库名 | Schema 归属 |
|---|---|---|
| `catalog` | `catalog` | 仅 catalog 服务 |
| `order` | `order` | 仅 order 服务 |
| `notification` | `notification` | 仅 notification 服务 |

### 数据快照模式

下单时，`order` 服务通过 REST 调用 `catalog` 以验证库存并获取当前书价，随后将该数据的**快照**存入 `order` 数据库：

```sql
-- order: order_items table
CREATE TABLE order_items (
    id           UUID PRIMARY KEY,
    order_id     UUID NOT NULL REFERENCES orders(id),
    book_id      UUID NOT NULL,        -- reference only, no FK to catalog
    book_title   VARCHAR(500) NOT NULL, -- snapshot at order time
    unit_price   BIGINT NOT NULL,       -- snapshot at order time (cents)
    quantity     INT NOT NULL
);
```

这样即使书目录中的价格或书名后续发生变更，历史订单记录也始终保持稳定。

---

## 影响

### 积极影响
- **独立可部署性**：每个服务可以独立部署、扩缩容和迁移。
- **故障隔离**：`catalog` 的 PostgreSQL 发生故障不会影响 `order` 对已有订单的读取。
- **技术自由度**：每个服务理论上可以使用不同数据库（例如 `notification` 可迁移至 MongoDB 而不影响其他服务）。
- **归属清晰**：每张表归属于且仅归属于一个服务，schema 变更的职责明确无歧义。

### 消极影响
- **数据冗余**：书名和价格同时存储于 `catalog` 和 `order`，这是有意为之且可接受的。
- **无跨服务事务**：下单涉及库存预留（catalog）和订单创建（order）两个服务，必须通过 Saga 模式或补偿事务处理，而非数据库事务。
- **最终一致性**：catalog 的库存数量与 order 服务所见的库存视图可能短暂出现偏差。

### 订单下单的 Saga 流程

下单流程横跨两个服务：

```
1. order: create Order in PENDING state (order)
2. order: call catalog to reserve stock (REST)
   a. Success → order: transition Order to CONFIRMED; publish OrderPlaced
   b. Failure → order: transition Order to CANCELLED; publish OrderCancelled
3. catalog: consume StockReserved event → decrement stock in catalog
```

这是一个**基于编排的 Saga**——没有中央协调者，每个服务响应事件并在失败时执行补偿。

### 本地开发

单个 PostgreSQL 实例（通过基础设施 Helm Chart 部署）运行三个逻辑数据库，由 `infrastructure/db/init.sh` 初始化：

```sql
CREATE DATABASE catalog;
CREATE DATABASE "order";
CREATE DATABASE notification;
```

在生产环境（Kubernetes）中，每个服务使用由 PostgreSQL Operator（如 CloudNativePG）管理的独立 PostgreSQL 实例。
