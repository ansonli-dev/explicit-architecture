# 项目结构规范指南

本文档以 **order-service** 为例，详细说明基于
**DDD + Hexagonal (Ports & Adapters) + CQRS** 的代码组织规范。

> 同一套规范适用于 `catalog`、`order`、`notification` 三个服务，
> 只有 `infrastructure/` 适配器按服务实际使用的技术栈裁剪。

---

## 总体原则

1. **依赖方向**：`interfaces/` 和 `infrastructure/` 依赖 `application/`，`application/` 依赖 `domain/`，反向严禁。
2. **领域纯净**：`domain/` 不得出现任何框架注解或 I/O 操作——纯 Java。
3. **CQRS 拆分**：`command/`（写操作）和 `query/`（读操作）在应用层严格分离，各自拥有独立的 Handler。
4. **按特性分包**：在 `application/command/` 和 `application/query/` 中，按聚合（如 `order`）纵向分包，而非把所有 Command/Query 平铺在一起。
5. **适配器分边**：REST Controller 和 Kafka Consumer（主动侧）放 `interfaces/`；JPA、Redis、Kafka Producer、HTTP Client（被动侧）放 `infrastructure/`。
6. **CommandBus / QueryBus 作为主动侧入口**：Controller 只依赖 `CommandBus` 和 `QueryBus` 两个接口，不直接注入具体 Handler 类；不定义每用例的 inbound port 接口。

---

## 目录结构全览（order-service）

```text
com.example.order/
├── domain/              # 零框架依赖 — 纯 Java 领域模型
├── application/         # 用例编排 — 只依赖 domain
├── infrastructure/      # 被动侧适配器 — JPA、Outbox、ElasticSearch、HTTP Client
└── interfaces/          # 主动侧适配器 — REST Controller、Kafka Consumer
```

---

## 详细层级说明

### 1. `domain/` — 领域层（最内层）

**职责**：业务规则的"真相来源"。包含聚合根、实体、值对象、领域事件、领域服务。

**规范**：
- 不依赖 `application/` 或 `infrastructure/` 中的任何类。
- Repository 接口不在此定义（见 `application/port/outbound/`）。
- 零框架注解——不允许出现 `@Component`、`@Entity`、`@Transactional` 等任何注解。

```text
domain/
├── model/
│   ├── Order.java              # 聚合根：封装订单状态机和不变量
│   ├── OrderItem.java          # 实体：属于 Order 聚合，快照书名和单价
│   ├── OrderId.java            # 值对象 (record)
│   ├── CustomerId.java         # 值对象 (record)
│   ├── Money.java              # 值对象 (record)：金额 + 货币，不可变
│   ├── OrderStatus.java        # Sealed Interface：Pending / Confirmed / Shipped / Cancelled
│   ├── PricingResult.java      # 值对象 (record)：定价服务的计算结果
│   └── OrderStateException.java # 领域异常：非法状态迁移
├── event/
│   ├── OrderPlaced.java        # 领域事件 (record)：订单已下单
│   ├── OrderConfirmed.java     # 领域事件 (record)：订单已确认
│   ├── OrderShipped.java       # 领域事件 (record)：订单已发货
│   └── OrderCancelled.java     # 领域事件 (record)：订单已取消
└── service/
    └── OrderPricingService.java # 领域服务：跨 OrderItem 的折扣计算逻辑
```

> **何时使用 Domain Service**：操作无状态、不自然归属于单个聚合、
> 需要跨多个领域对象协作时（如定价折扣需要遍历所有 `OrderItem`）。

---

### 2. `application/` — 应用层

**职责**：用例编排——调用领域对象，通过端口接口依赖外部能力。

**规范**：
- 不得导入 Spring、JPA、Kafka、Redis 等框架类（`@Service` 和 Lombok 除外）。
- CommandHandler / QueryHandler 标注 `@Service`，由 Spring 自动注入依赖。
- `@Transactional` 不放在 Handler 上，放在 `infrastructure/repository/jpa/` 适配器中。
- Controller 通过 `CommandBus` / `QueryBus` 调用 Handler，不直接注入具体 Handler 类，也不定义每用例的 inbound port 接口。

```text
application/
├── port/
│   └── outbound/                       # 二级端口：本服务对外部的依赖声明
│       ├── OrderPersistence.java       # → JPA 实现（infrastructure/repository/jpa/）
│       ├── OrderSearchRepository.java  # → ElasticSearch 实现（infrastructure/repository/elasticsearch/）
│       └── CatalogClient.java          # → HTTP 实现（infrastructure/client/）
├── command/
│   └── order/                         # 按聚合分包
│       ├── PlaceOrderCommand.java      # 命令 record（写侧入参，无 HTTP 信息）
│       ├── PlaceOrderCommandHandler.java   # @Service, implements CommandHandler<PlaceOrderCommand, OrderId>
│       ├── CancelOrderCommand.java     # 命令 record
│       └── CancelOrderCommandHandler.java  # @Service, implements CommandHandler<CancelOrderCommand, Void>
└── query/
    └── order/                         # 按聚合分包
        ├── GetOrderQuery.java          # 查询 record：按 ID 查单条
        ├── GetOrderQueryHandler.java   # @Service, implements QueryHandler<GetOrderQuery, OrderDetailResponse>，直接查 ES
        ├── ListOrdersQuery.java        # 查询 record：分页 + 过滤条件
        ├── ListOrdersQueryHandler.java # @Service, implements QueryHandler<ListOrdersQuery, List<OrderSummaryResponse>>
        ├── OrderDetailResponse.java    # 响应 DTO record（单条订单详情）
        ├── OrderItemResponse.java      # 响应 DTO record（订单项）
        ├── OrderSummaryResponse.java   # 响应 DTO record（列表摘要）
        └── OrderNotFoundException.java # 应用层异常：订单不存在
```

> **命令与查询的区别**：
> - `CommandHandler`：改变系统状态，写 PostgreSQL，发布领域事件（通过 Outbox，非直接调用）
> - `QueryHandler`：只读，直接查 ElasticSearch 读模型，不触及领域层

---

### 3. `infrastructure/` — 基础设施层（被动侧适配器）

**职责**：实现 `application/port/outbound/` 中定义的所有次级端口接口。

**规范**：
- 只有这里才能引入 JPA / Kafka / Redis / Elasticsearch SDK。
- JPA Entity（`*JpaEntity`）不得暴露到此包外——Mapper 负责与领域对象互转。
- `@Transactional` 注解只在持久化适配器中使用。
- Outbox 基础表（`outbox_event`）及其 JPA 实体、Relay 调度器均由 seedwork 提供，不在服务代码中重复定义。

```text
infrastructure/
├── repository/
│   ├── jpa/
│   │   ├── OrderPersistenceAdapter.java  # 实现 OrderPersistence（含 @Transactional）
│   │   ├── OrderJpaEntity.java           # JPA 实体（@Entity），不出此包
│   │   ├── OrderItemJpaEntity.java       # JPA 实体
│   │   └── OrderJpaRepository.java       # Spring Data JPA 接口
│   └── elasticsearch/
│       ├── OrderSearchAdapter.java       # 实现 OrderSearchRepository
│       ├── OrderElasticDocument.java     # ES 文档模型
│       └── OrderElasticRepository.java   # Spring Data ES 接口
├── messaging/
│   └── outbox/
│       └── OrderOutboxMapper.java        # 实现 seedwork OutboxMapper SPI（领域事件 → Avro payload）
└── client/
    └── CatalogRestClient.java            # 实现 CatalogClient（WebClient HTTP 调用）
```

---

### 4. `interfaces/` — 接口层（主动侧适配器）

**职责**：系统入口——接收外部请求（HTTP、Kafka 消息），将其转为 Command / Query，通过 `CommandBus` / `QueryBus` 调用对应 Handler。

**规范**：
- 禁止在 Controller 中编写业务逻辑。
- Controller 将 HTTP 入参映射为 Command / Query 对象后，通过 `commandBus.dispatch()` / `queryBus.dispatch()` 调用——不直接注入具体 Handler 类。
- Kafka Consumer 将消息反序列化为内部对象后，同样通过 CommandBus 调用应用层。

```text
interfaces/
├── rest/
│   ├── OrderCommandController.java     # POST /api/v1/orders, PUT /api/v1/orders/{id}/cancel
│   └── OrderQueryController.java       # GET /api/v1/orders/{id}, GET /api/v1/orders
└── messaging/
    └── consumer/
        ├── OrderEventConsumer.java     # @IdempotentKafkaListener 单一入口，路由到各 Handler
        └── OrderPlacedHandler.java     # 处理 OrderPlaced 事件，dispatch 到 CommandBus
```

---

## 核心执行流程（下单）

```
HTTP POST /api/v1/orders
  ↓
OrderCommandController          [interfaces/rest/]
  → commandBus.dispatch(new PlaceOrderCommand(...))
  ↓
PlaceOrderCommandHandler        [application/command/order/]
  1. CatalogClient.checkStock() & reserveStock()
  2. OrderPricingService.calculate()            ← Domain Service [domain/service/]
  3. Order.create(items, finalTotal)             ← Aggregate [domain/model/]
  4. Order.place() → registers OrderPlaced      ← Domain Event（内存注册）[domain/event/]
  5. OrderPersistence.save(order)               ← AbstractAggregateRootEntity 携带事件
  ↓
OutboxWriteListener (BEFORE_COMMIT)             [seedwork]
  → 在同一事务中原子写入 outbox_event 表
  ↓
Debezium CDC / OutboxRelayScheduler             [seedwork / 基础设施]
  → 发布到 Kafka topic: bookstore.order.placed
```

---

## 层间命名速查

| 概念 | 命名模式 | 示例 |
|---|---|---|
| Command record | `{Action}{Aggregate}Command` | `PlaceOrderCommand` |
| CommandHandler | `{Action}{Aggregate}CommandHandler` | `PlaceOrderCommandHandler` |
| Query record | `{Criteria}{Aggregate}Query` | `GetOrderQuery`、`ListOrdersQuery` |
| QueryHandler | `{Criteria}{Aggregate}QueryHandler` | `ListOrdersQueryHandler` |
| Response DTO | `{Aggregate}{Purpose}Response` | `OrderDetailResponse` |
| Repository Port | `{Aggregate}Persistence` | `OrderPersistence` |
| Read Model Port | `{Aggregate}SearchRepository` | `OrderSearchRepository` |
| Service Client Port | `{Target}Client` | `CatalogClient` |
| Domain Event | `{Aggregate}{PastTense}` | `OrderPlaced` |
| JPA Entity | `{Aggregate}JpaEntity` | `OrderJpaEntity` |
| JPA Repository | `{Aggregate}JpaRepository` | `OrderJpaRepository` |
| ES Document | `{Aggregate}ElasticDocument` | `OrderElasticDocument` |
| ES Repository | `{Aggregate}ElasticRepository` | `OrderElasticRepository` |
| REST Controller（写）| `{Aggregate}CommandController` | `OrderCommandController` |
| REST Controller（读）| `{Aggregate}QueryController` | `OrderQueryController` |
| Outbox Mapper 适配器 | `{Service}OutboxMapper` | `OrderOutboxMapper` |
| Kafka Consumer 适配器 | `{Event}Consumer` | `OrderEventConsumer` |
| HTTP Client 适配器 | `{Target}RestClient` | `CatalogRestClient` |
