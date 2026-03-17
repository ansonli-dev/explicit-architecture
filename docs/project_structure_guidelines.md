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

---

## 目录结构全览（order-service）

```text
com.example.order/
├── domain/              # 零框架依赖 — 纯 Java 领域模型
├── application/         # 用例编排 — 只依赖 domain
├── infrastructure/      # 被动侧适配器 — JPA、Kafka Producer、ElasticSearch、HTTP Client
└── interfaces/          # 主动侧适配器 — REST Controller、Kafka Consumer
```

---

## 详细层级说明

### 1. `domain/` — 领域层（最内层）

**职责**：业务规则的"真相来源"。包含聚合根、实体、值对象、领域事件、领域服务。

**规范**：
- 不依赖 `application/` 或 `infrastructure/` 中的任何类。
- Repository 接口不在此定义（见 `application/port/outbound/`）。

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
- 不得导入 Spring、JPA、Kafka、Redis 等框架类。
- CommandHandler / QueryHandler 标注 `@Service`，由 Spring 自动注入依赖。
- `@Transactional` 不放在 Handler 上，放在 `infrastructure/persistence/` 适配器中。
- Controller 依赖 `port/inbound/` 接口，而非具体 Handler 类——这是六边形的主动侧端口。

```text
application/
├── port/
│   ├── inbound/                        # 一级端口：本服务对外暴露的能力清单
│   │   ├── PlaceOrderUseCase.java      # → PlaceOrderCommandHandler 实现
│   │   ├── CancelOrderUseCase.java     # → CancelOrderCommandHandler 实现
│   │   ├── GetOrderUseCase.java        # → GetOrderQueryHandler 实现
│   │   └── ListOrdersUseCase.java      # → ListOrdersQueryHandler 实现
│   └── outbound/                       # 二级端口：本服务对外部的依赖声明
│       ├── OrderPersistence.java       # → JPA 实现
│       ├── OrderSearchRepository.java  # → ElasticSearch 实现
│       ├── EventDispatcher.java        # → Kafka 实现
│       ├── OutboxRepository.java       # → JPA 实现
│       ├── OutboxEntry.java            # DTO record：Outbox 条目
│       └── CatalogClient.java          # → HTTP 实现（infrastructure/client/）
├── command/
│   └── order/                         # 按聚合分包
│       ├── PlaceOrderCommand.java      # 命令 record（写侧入参，无 HTTP 信息）
│       ├── PlaceOrderCommandHandler.java   # @Service, implements PlaceOrderUseCase
│       ├── CancelOrderCommand.java     # 命令 record
│       └── CancelOrderCommandHandler.java  # @Service, implements CancelOrderUseCase
└── query/
    └── order/                         # 按聚合分包
        ├── GetOrderQueryHandler.java   # @Service, implements GetOrderUseCase，直接查 ES
        ├── ListOrdersQuery.java        # 查询 record：分页 + 过滤条件
        ├── ListOrdersQueryHandler.java # @Service, implements ListOrdersUseCase
        ├── OrderDetailResponse.java    # 响应 DTO record（单条订单详情）
        ├── OrderItemResponse.java      # 响应 DTO record（订单项）
        ├── OrderSummaryResponse.java   # 响应 DTO record（列表摘要）
        └── OrderNotFoundException.java # 应用层异常：订单不存在
```

> **命令与查询的区别**：
> - `CommandHandler`：改变系统状态，写 PostgreSQL，发布领域事件
> - `QueryHandler`：只读，直接查 ElasticSearch 读模型，不触及领域层

---

### 3. `infrastructure/` — 基础设施层（被动侧适配器）

**职责**：实现 `application/port/outbound/` 中定义的所有次级端口接口。

**规范**：
- 只有这里才能引入 JPA / Kafka / Redis / Elasticsearch SDK。
- JPA Entity（`*JpaEntity`）不得暴露到此包外——Mapper 负责与领域对象互转。
- `@Transactional` 注解只在持久化适配器中使用。

```text
infrastructure/
├── persistence/
│   └── jpa/
│       ├── OrderPersistenceAdapter.java  # 实现 OrderPersistence（含 @Transactional）
│       ├── OutboxPersistenceAdapter.java # 实现 OutboxRepository
│       ├── OrderJpaEntity.java           # JPA 实体（@Entity），不出此包
│       ├── OrderItemJpaEntity.java       # JPA 实体
│       ├── OutboxEventJpaEntity.java     # JPA 实体：Outbox 表
│       ├── OrderJpaRepository.java       # Spring Data JPA 接口
│       └── OutboxJpaRepository.java      # Spring Data JPA 接口
├── search/
│   └── elasticsearch/
│       ├── OrderSearchAdapter.java       # 实现 OrderSearchRepository
│       ├── OrderElasticDocument.java     # ES 文档模型
│       └── OrderElasticRepository.java   # Spring Data ES 接口
├── messaging/
│   └── kafka/
│       ├── KafkaEventDispatcherAdapter.java  # 实现 EventDispatcher（Avro + Kafka）
│       └── OutboxRelayScheduler.java         # @Scheduled：轮询 Outbox，发布到 Kafka
└── client/
    └── CatalogRestClient.java            # 实现 CatalogClient（WebClient HTTP 调用）
```

---

### 4. `interfaces/` — 接口层（主动侧适配器）

**职责**：系统入口——接收外部请求（HTTP、Kafka 消息），将其转为 Command / Query，调用对应 Handler。

**规范**：
- 禁止在 Controller 中编写业务逻辑。
- Controller 将 HTTP 入参映射为 Command / Query 对象后直接交给 Handler。
- Kafka Consumer 将消息反序列化为内部对象后调用 CommandHandler 或 QueryHandler。

```text
interfaces/
└── rest/
    ├── OrderCommandController.java     # POST /api/v1/orders, PUT /api/v1/orders/{id}/cancel
    └── OrderQueryController.java       # GET /api/v1/orders/{id}, GET /api/v1/orders
```

---

## 核心执行流程（下单）

```
HTTP POST /api/v1/orders
  │
  ▼
OrderCommandController          [interfaces/rest/]
  │  将请求体映射为 PlaceOrderCommand
  ▼
PlaceOrderCommandHandler        [application/command/order/]
  │  1. 调 CatalogClient.checkStock() & reserveStock()
  │  2. 调 OrderPricingService.calculate()     ← Domain Service [domain/service/]
  │  3. 调 Order.create(items, finalTotal)     ← Aggregate [domain/model/]
  │  4. 调 Order.place()  → 返回 OrderPlaced   ← Domain Event [domain/event/]
  │  5. 调 OrderPersistence.save(order)
  │  6. 调 EventDispatcher.publishOrderPlaced(event)
  ▼
OrderPersistenceAdapter         [infrastructure/persistence/jpa/]
  │  写 orders + outbox_event 表（同一事务）
  ▼
KafkaEventDispatcherAdapter     [infrastructure/messaging/kafka/]
     通过 Outbox Relay 异步发布到 Kafka topic: order.placed
```

---

## 层间命名速查

| 概念 | 命名模式 | 示例 |
|---|---|---|
| Command record | `{Action}{Aggregate}Command` | `PlaceOrderCommand` |
| CommandHandler | `{Action}{Aggregate}CommandHandler` | `PlaceOrderCommandHandler` |
| Query record | `{Criteria}{Aggregate}Query` | `FindOrdersByCustomerQuery` |
| QueryHandler | `{Criteria}{Aggregate}QueryHandler` | `ListOrdersQueryHandler` |
| Response DTO | `{Aggregate}{Purpose}Response` | `OrderDetailResponse` |
| Repository Port | `{Aggregate}Persistence` | `OrderPersistence` |
| Event Dispatcher Port | `EventDispatcher` | `EventDispatcher` |
| Domain Event | `{Aggregate}{PastTense}` | `OrderPlaced` |
| JPA Entity | `{Aggregate}JpaEntity` | `OrderJpaEntity` |
| JPA Repository | `{Aggregate}JpaRepository` | `OrderJpaRepository` |
| REST Controller | `{Aggregate}Controller` | `OrderCommandController` |
| Kafka Producer Adapter | `Kafka{Aggregate}EventPublisher` | `KafkaOrderEventPublisher` |
| Kafka Consumer Adapter | `{Event}Consumer` | `OrderPlacedConsumer` |
| HTTP Client Adapter | `{Target}RestClient` | `CatalogRestClient` |
| Mapper | `{Aggregate}{From}To{To}Mapper` | `OrderJpaEntityToDomainMapper` |
