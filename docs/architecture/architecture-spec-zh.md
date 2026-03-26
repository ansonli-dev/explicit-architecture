# 架构规范

**在线书店 — 显式架构 + 澄清架构**

> 版本 1.0 · 2026年3月
>
> **权威链条：** [`clarified-architecture-en.md`](clarified-architecture/clarified-architecture-en.md) 是架构原则的权威来源。本文档将这些原则转化为项目特定的命名规范、包结构和实现规则。如果本文档与 `clarified-architecture-en.md` 存在冲突，以后者为准。如果本文档与 `CLAUDE.md` 存在冲突，以本文档为准。
>
> 上游参考：
> - [Herberto Graça — Explicit Architecture](https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/)
> - [`clarified-architecture-en.md`](clarified-architecture/clarified-architecture-en.md) — 权威架构原则（具有最高优先级）
> - 项目代码 — 实际实现的唯一真实来源

---

## 目录

- [第一部分：哲学与基础](#第一部分哲学与基础)
- [第二部分：五项澄清](#第二部分五项澄清)
- [第三部分：包结构](#第三部分包结构)
- [第四部分：CQRS 实现](#第四部分cqrs-实现)
- [第五部分：事件驱动架构](#第五部分事件驱动架构)
- [第六部分：事务规则](#第六部分事务规则)
- [第七部分：命名约定](#第七部分命名约定)
- [第八部分：测试策略](#第八部分测试策略)
- [第九部分：反模式](#第九部分反模式)
- [第十部分：技术决策（ADR 索引）](#第十部分技术决策adr-索引)

---

## 第一部分：哲学与基础

### 1.1 为什么选择显式架构

传统的 Spring Boot 分层架构（`controller → service → repository`）存在三个系统性问题：

1. **框架泄漏** — `@Transactional`、JPA 注解和 Spring 构造型注解最终进入业务逻辑，使其在没有 Spring 上下文的情况下无法测试。
2. **没有强制边界** — 没有任何机制阻止控制器直接调用仓储。
3. **意图隐藏** — 命名为 `service/` 的包无法表达应用程序的实际用途。

显式架构将 DDD、六边形（端口与适配器）、洋葱架构和整洁架构融合为一个统一模型。每一层都有反映其*职责*的名称，而非技术层级。业务逻辑无需任何基础设施即可测试。

### 1.2 铁律

> **领域模型不依赖自身之外的任何东西。**
>
> 不依赖基础设施接口，不依赖应用层类型，不依赖具有运行时行为的框架注解。这是唯一不可放宽的不变式。

"运行时行为"在实践中的含义：

| 注解 | 判定 | 原因 |
|------------|---------|--------|
| `@Entity` | **领域层禁止** | 改变对象被 JPA 加载和刷新的方式 |
| `@Transactional` | **领域/服务层禁止** | 生成 Spring 代理，产生隐藏的基础设施依赖 |
| `@OneToMany`, `@ManyToOne` | **领域层禁止** | JPA 关系管理 — 基础设施关注点 |
| `@Lazy` | **领域层禁止** | 加载策略 — 基础设施关注点 |
| `@Component`, `@Service` | **领域服务上可接受** | 仅用于发现；无运行时行为；`new DomainService()` 的行为完全相同 |

### 1.3 四个区域

| 区域 | 包含内容 | 依赖于 | 关键约束 |
|------|----------|------------|----------------|
| **领域模型** | 实体、值对象、领域事件、规格 | 无 | 零应用层/基础设施/框架导入 |
| **领域服务** | 无状态跨聚合纯逻辑 | 仅领域模型 | 无 I/O；不注入仓储；无 `@Transactional` |
| **应用层** | 处理器、命令、查询、端口、DTO | 领域模型 + 领域服务 | 仅编排；无 JPA/Redis/Kafka 导入 |
| **基础设施 + 接口** | 适配器、控制器、ORM、消息 | 通过端口访问所有内层 | 实现端口；永不被内层导入 |

### 1.4 依赖方向

```
Interfaces ──┐
             ├──► Application ──► Domain Services ──► Domain Model
Infrastructure┘
```

在运行时，流向反转：控制器调用处理器，处理器调用领域，领域对调用者一无所知。

### 1.5 端口与适配器

- **主要（驱动）适配器** — REST 控制器、Kafka 消费者。它们位于 `interfaces/`，接收外部输入，并通过总线分发命令/查询。
- **次要（被驱动）适配器** — JPA 仓储、Redis 客户端、Kafka 生产者、HTTP 客户端。它们位于 `infrastructure/`，实现由应用层（或领域层用于写侧仓储）定义的端口。
- **总线作为主要端口** — `CommandBus` 和 `QueryBus` 是驱动侧的单一入口点。控制器只依赖这两个接口，永不注入具体的处理器类。

---

## 第二部分：五项澄清

Herberto Graça 的文章留下了五个开放的决策点。本项目对每个决策点做出明确的、全项目统一的选择。

### 2.1 澄清 1 — 用例的唯一归宿

**问题：** 显式架构允许逻辑存在于应用服务*或*命令处理器中，造成歧义。

**决策：** 本项目使用**通过总线的命令/查询处理器**作为唯一的用例容器。应用服务类不存在。命名为 `*Service` 的类只作为领域服务存在。

```java
// 处理器结构 — 用例逻辑唯一存在的地方
@Service
public class PlaceOrderCommandHandler implements CommandHandler<PlaceOrderCommand, PlaceOrderResult> {
    @Override
    public PlaceOrderResult handle(PlaceOrderCommand cmd) {
        // 1. 通过端口加载（此处无直接 JPA/Kafka）
        // 2. 调用领域服务/聚合方法
        // 3. 通过端口持久化
        // 4. 不分发事件 — 发布是持久化的副作用
    }
}
```

### 2.2 澄清 2 — 领域服务的纯粹性

**问题：** 当领域服务需要自身无法获取的数据时（因为它不能持有仓储引用），层次边界会变得模糊。

**决策：** 领域服务是**纯函数**。它们以参数形式接收所有输入。处理器执行所有 I/O。

```java
// 领域服务 — 纯粹；不注入任何端口
public class OrderPricingService {
    public PricingResult calculate(List<OrderItem> items, DiscountPolicy policy) {
        // 仅纯计算
    }
}

// 处理器 — 所有 I/O 在此完成，然后委托
public PlaceOrderResult handle(PlaceOrderCommand cmd) {
    var items = /* 加载条目 */;
    var policy = /* 加载策略 */;
    var pricing = pricingService.calculate(items, policy); // 纯调用
    // 持久化...
}
```

### 2.3 澄清 3 — 仓储接口的位置

**问题：** 仓储接口应位于领域层还是应用层？

**决策：清晰拆分写侧和读侧。**

| 路径 | 端口位置 | 理由 |
|------|--------------|-----------|
| **写侧**（命令） | `domain/ports/` | `OrderPersistence` 是一个领域概念 — "所有订单的集合"。表示领域对象的参数使用领域类型（`OrderId`、`Order`），而非原始 `UUID`/`String`；用于分页或简单过滤的基本类型是可接受的。 |
| **读侧**（查询） | `application/port/outbound/` | `OrderSearchRepository`、`OrderReadRepository` 是没有领域意义的基础设施抽象。 |

```
domain/ports/
└── OrderPersistence.java       ← save(Order), findById(OrderId)

application/port/outbound/
├── OrderSearchRepository.java  ← findById(UUID) → OrderDetailResponse（绕过领域）
├── OrderReadRepository.java    ← findDetailById(OrderId) → OrderDetailResponse（JPA 投影）
└── CatalogClient.java          ← checkStock/reserve/release（HTTP 跨服务调用）
```

关键区别：`OrderPersistence` 只使用领域类型（`Order`、`OrderId`）。`OrderSearchRepository` 直接返回 DTO — 它没有领域意义。

### 2.4 澄清 4 — 事件注册中心而非共享内核

**问题：** 共享 Java 类的共享内核会无限增长并耦合所有服务。

**决策：** `shared-events` 模块是一个**事件注册中心** — 一个仅包含模式生成类、不含业务逻辑的 Avro SDK。

```
shared-events/src/main/avro/
└── com/example/events/v1/
    ├── OrderPlaced.avsc
    ├── OrderCancelled.avsc
    ├── StockReserved.avsc
    └── ...

⬇ 构建时 Avro 代码生成

shared-events/build/generated/avro/
└── com/example/events/v1/
    ├── OrderPlaced.java        ← SpecificRecord，无业务逻辑
    └── ...
```

规则：
- 领域对象（`domain/event/`）永不导入 Avro 类。
- 只有 `infrastructure/messaging/outbox/{Service}OutboxMapper.java` 负责将领域事件映射为 Avro。
- 模式变更遵循 [ADR-008](adr/ADR-008-shared-events-versioning.md)：向后兼容变更 → PATCH/MINOR 版本号提升；破坏性变更 → 新版本命名空间（`v2/`）+ MAJOR 版本号提升。

### 2.5 澄清 5 — 跨服务数据一致性

**问题：** 微服务之间需要彼此的数据。共享数据库会产生耦合。

**决策：** 通过**发件箱模式**实现事件驱动的本地副本（而非共享数据库视图 — 我们处于微服务拓扑中）。

每个服务独占其 PostgreSQL 数据库。跨服务数据通过 Kafka 流动：

```
订单下达 → OrderPlaced 事件写入发件箱 → Kafka → 通知服务消费
目录释放库存 → OrderCancelled 事件被目录服务消费 → 库存释放
```

跨服务状态变更（如取消订单时释放库存）**始终是事件驱动的**。不通过同步 HTTP 调用进行跨服务状态变更。

---

## 第三部分：包结构

### 3.1 规范布局（所有服务通用）

```
com.example.{service}/
├── domain/                          ← 零框架依赖。纯 Java。
│   ├── model/                       ← 聚合、实体、值对象（记录类）、枚举
│   ├── event/                       ← 领域事件（实现 DomainEvent 的记录类）
│   ├── ports/                       ← 写侧仓储接口（领域对象使用领域类型而非原始 UUID/String；分页/过滤用基本类型可接受）
│   └── service/                     ← 领域服务（无状态、无 I/O、无 @Transactional）
│
├── application/                     ← @Service、Lombok 可用。无 JPA/Redis/Kafka 导入。
│   ├── command/
│   │   └── {aggregate}/             ← 按特性分包
│   │       ├── {Action}{Agg}Command.java
│   │       ├── {Action}{Agg}CommandHandler.java
│   │       └── {Action}{Agg}Result.java
│   ├── query/
│   │   └── {aggregate}/             ← 按特性分包
│   │       ├── {Criteria}{Agg}Query.java
│   │       ├── {Criteria}{Agg}QueryHandler.java
│   │       └── {Agg}{Purpose}Result.java
│   └── port/
│       └── outbound/                ← 非领域概念的次要端口
│           ├── {Agg}SearchRepository.java   ← ES 读模型
│           ├── {Agg}ReadRepository.java     ← JPA 读投影（备用）
│           ├── {Agg}Cache.java              ← Redis（如适用）
│           └── {Target}Client.java          ← HTTP 跨服务调用
│
├── infrastructure/                  ← 所有框架代码均在此。@Transactional 在此。
│   ├── repository/
│   │   ├── jpa/
│   │   │   ├── {Agg}JpaEntity.java          ← @Entity；不得暴露在此包之外
│   │   │   ├── {Agg}JpaRepository.java      ← Spring Data 接口
│   │   │   └── {Agg}PersistenceAdapter.java ← 实现领域端口；@Transactional 在此
│   │   └── elasticsearch/           ← 仅 order 服务
│   │       ├── {Agg}ElasticDocument.java
│   │       ├── {Agg}ElasticRepository.java
│   │       └── {Agg}SearchAdapter.java
│   ├── messaging/
│   │   └── outbox/
│   │       └── {Service}OutboxMapper.java   ← 将领域事件映射为 Avro OutboxEntry
│   ├── cache/                       ← 仅 catalog 服务
│   │   └── Redis{Agg}Cache.java
│   ├── client/                      ← 仅 order 服务（到 catalog 的 HTTP 客户端）
│   │   └── {Target}RestClient.java
│   └── email/                       ← 仅 notification 服务
│       └── LogEmailAdapter.java
│
└── interfaces/                      ← 主要（驱动）适配器。无业务逻辑。
    ├── rest/
    │   ├── {Agg}CommandController.java
    │   ├── {Agg}QueryController.java
    │   ├── request/                 ← HTTP 请求体 DTO（每个带有请求体的端点一个记录类）
    │   │   └── {Action}{Agg}Request.java
    │   └── response/                ← HTTP 响应 DTO（在控制器中从 *Result 映射）
    │       └── {Agg}{Purpose}Response.java
    ├── messaging/
    │   └── consumer/                ← 仅在消费 Kafka 事件的服务中存在
    │       ├── {Topic}EventConsumer.java    ← 单一 @KafkaListener 入口点
    │       └── {Event}Handler.java          ← 按事件处理，分发到 CommandBus
    └── event/                       ← 同服务领域事件监听器（含业务语义）
        └── {Event}{Reaction}Listener.java  ← 通过 CommandBus 分发 Command
```

### 3.2 各服务实际结构

**catalog**（端口 8081 | PostgreSQL + Redis）：

```
domain/model/    Book, BookId, Author, Title, Category, Money, StockLevel, InsufficientStockException
domain/event/    BookAdded, StockReserved, StockReleased
domain/ports/    BookPersistence
application/command/book/    AddBook, UpdateBook, ReserveStock, ReleaseStock (+ Results)
application/query/book/      GetBook, ListBooks, GetStock (+ Responses)
application/port/outbound/   BookCache
infrastructure/repository/jpa/   BookJpaEntity, BookJpaRepository, BookPersistenceAdapter, BookEntityMapper
infrastructure/cache/        RedisBookCache, BookCacheInvalidationListener, BookPersistedEvent
infrastructure/messaging/outbox/ CatalogOutboxMapper
interfaces/rest/             BookCommandController, BookQueryController
interfaces/messaging/consumer/   OrderEventConsumer, OrderCancelledHandler
```

**order**（端口 8082 | PostgreSQL + ElasticSearch — CQRS）：

```
domain/model/    Order, OrderId, OrderItem, CustomerId, Money, PricingResult, OrderStatus, OrderStateException, InsufficientStockException
domain/event/    OrderPlaced, OrderConfirmed, OrderShipped, OrderCancelled
domain/ports/    OrderPersistence
domain/service/  OrderPricingService
application/command/order/   PlaceOrder, CancelOrder, AutoConfirmOrder (+ PlaceOrderResult)
application/query/order/     GetOrder, ListOrders (+ OrderDetailResult, OrderItemResult, OrderSummaryResult)
application/port/outbound/   CatalogClient, StockAvailability, OrderSearchRepository, OrderReadRepository
infrastructure/repository/jpa/         OrderJpaEntity, OrderJpaRepository, OrderPersistenceAdapter
infrastructure/repository/elasticsearch/ OrderElasticDocument, OrderElasticRepository, OrderSearchAdapter
infrastructure/messaging/outbox/        OrderOutboxMapper
infrastructure/client/                  CatalogRestClient
interfaces/rest/             OrderCommandController, OrderQueryController
interfaces/rest/request/     PlaceOrderRequest, CancelOrderRequest
interfaces/rest/response/    PlaceOrderResponse, OrderDetailResponse, OrderSummaryResponse
interfaces/messaging/consumer/ OrderEventConsumer, OrderCancelledHandler
interfaces/event/            OrderPlacedAutoConfirmListener
```

**notification**（端口 8083 | PostgreSQL）：

```
domain/model/    Notification, NotificationId, Channel, DeliveryStatus, Payload
domain/event/    NotificationSent, NotificationFailed
domain/ports/    NotificationRepository
application/command/notification/  SendNotification
application/query/notification/    ListNotifications (+ NotificationResponse)
application/port/outbound/         CustomerClient, EmailSender
infrastructure/repository/jpa/     NotificationJpaEntity, NotificationJpaRepository, NotificationPersistenceAdapter
infrastructure/client/customer/    StubCustomerClient
infrastructure/client/email/       LogEmailAdapter
interfaces/rest/                   NotificationController
interfaces/messaging/consumer/     OrderEventConsumer, OrderPlacedHandler, OrderConfirmedHandler, OrderShippedHandler, OrderCancelledHandler
```

### 3.3 层规则执行

**`domain/` 规则：**
- 零来自 `application/`、`infrastructure/` 或任何框架的导入。
- 领域服务上的 `@Component`/`@Service` 是可接受的（仅用于发现的注解）。
- 禁止 `@Entity`、`@Transactional` 或 JPA 关系注解。
- 所有值对象 → Java `record`（不可变，结构相等）。
- 所有领域事件 → 实现 `DomainEvent` 的 Java `record`。

**`application/` 规则：**
- 禁止 JPA、Redis、Kafka 或框架 I/O 导入。
- 处理器上的 `@Service` 是可接受的。
- Lombok（`@Slf4j`、`@RequiredArgsConstructor`）是可接受的。
- 禁止 `@Transactional` — 它属于基础设施持久化适配器。
- 禁止直接注入具体处理器类 — 始终通过 `CommandBus`/`QueryBus`。
- 命令处理器返回 `{Action}{Agg}Result` 记录，**永不**返回查询层 DTO。

**`infrastructure/` 规则：**
- 唯一可以导入 JPA、Redis、Kafka 或 Elasticsearch SDK 的层。
- JPA 实体（`*JpaEntity`）不得暴露在其包之外 — 映射器负责与领域对象相互转换。
- `@Transactional` 注解属于持久化适配器方法，不属于处理器或领域服务。
- 一个持久化适配器可以实现多个端口（例如，`OrderPersistenceAdapter` 同时实现来自 `domain/ports/` 的 `OrderPersistence` 和来自 `application/port/outbound/` 的 `OrderReadRepository`）。

**`interfaces/` 规则：**
- 控制器中无业务逻辑。
- 控制器将 HTTP 输入映射为命令/查询 → `commandBus.dispatch()` / `queryBus.dispatch()`。
- Kafka 消费者反序列化 → 检查幂等性 → 分发到 CommandBus。
- **HTTP 请求体 DTO**：如果端点有 `@RequestBody`，在 `interfaces/dto/` 中定义一个 `{Action}{Agg}Request` 记录类。只有路径变量或查询参数的端点不需要请求对象。

---

## 第四部分：CQRS 实现

### 4.1 适用范围

CQRS（物理分离读写存储）**仅应用于 `order`**（参见 [ADR-002](adr/ADR-002-cqrs-scope-order-service.md)）。

- `catalog`：单一 PostgreSQL 加 Redis 缓存层 — 无 CQRS 开销的理由。
- `notification`：仅插入的日志，简单读取 — 无需 CQRS。
- `order`：针对高频写入工作负载的复杂查询（按客户、日期范围、状态、全文检索）— CQRS 有充分理由。

### 4.2 命令流（写路径）

```
HTTP POST /api/v1/orders
  ↓
OrderCommandController                    [interfaces/rest/]
  commandBus.dispatch(PlaceOrderCommand)
  ↓
PlaceOrderCommandHandler                  [application/command/order/]
  1. CatalogClient.checkStock()           ← HTTP 调用（事务外）
  2. CatalogClient.reserveStock()         ← HTTP 调用（事务外）
  3. OrderPricingService.calculate()      ← 领域服务（纯粹，内存中）
  4. Order.create(items, total)           ← 聚合工厂
  5. order.place()                        ← 注册 OrderPlaced 领域事件（内存中）
  6. OrderPersistence.save(order)         ← @Transactional 在此开启
       ↓
       BookEntityMapper 将领域事件附加到 AbstractAggregateRootEntity
       OutboxWriteListener (BEFORE_COMMIT) 原子性写入 outbox_event 行
       @Transactional 提交
  ↓
OutboxRelayScheduler                      [seedwork / infrastructure]
  将 OrderPlaced（Avro）发布到 Kafka 主题 bookstore.order.placed
  ↓
返回 PlaceOrderResult                     ← 从内存中的领域状态组装；零额外 I/O
```

### 4.3 查询流（读路径）

```
HTTP GET /api/v1/orders/{id}
  ↓
OrderQueryController                      [interfaces/rest/]
  queryBus.dispatch(GetOrderQuery)
  ↓
GetOrderQueryHandler                      [application/query/order/]
  1. OrderSearchRepository.findById()     ← Elasticsearch（主要）
     或 OrderReadRepository.findDetailById() ← JPA 投影（ES 不可用时备用）
  返回 OrderDetailResult                   ← 扁平 DTO；领域层永不加载
```

读路径**永不加载领域实体**。它直接从 ES 文档或 JPA 投影返回 DTO。

### 4.4 命令结果 vs 查询结果

这些是独立的类型，不得合并：

| | 类型 | 位置 | 组装来源 |
|--|------|----------|----------------|
| **命令结果** | `PlaceOrderResult` | `application/command/order/` | `save()` 后的内存中领域状态 — **零额外 I/O** |
| **查询结果** | `OrderDetailResult` | `application/query/order/` | ES 文档或 JPA 投影 — 可能需要 DB/ES 读取 |

`PlaceOrderResult` 和 `OrderDetailResult` 可能共享相似的字段，但它们是具有不同用途的独立记录类。耦合它们将在写侧和读侧之间创建双向依赖。

---

## 第五部分：事件驱动架构

### 5.1 领域事件（进程内）

领域事件是 `domain/event/` 中的纯 Java `record` 类，实现来自 seedwork 的 `DomainEvent`。

```java
// domain/event/OrderPlaced.java
public record OrderPlaced(
    UUID eventId,
    UUID orderId,
    UUID customerId,
    List<OrderItem> items,
    BigDecimal totalAmount,
    Instant occurredAt
) implements DomainEvent {}
```

它们通过 `registerDomainEvent(event)`（来自 `AggregateRoot`）在内存中注册到聚合上，永不由处理器直接分发。

### 5.2 集成事件（跨服务）

集成事件是 `shared-events/src/main/avro/com/example/events/v1/` 中的 Avro 模式。构建时生成 `SpecificRecord` Java 类，作为 `shared-events` 库发布。

| 事件 | 生产者 | 消费者 |
|-------|----------|-----------|
| `OrderPlaced` | order | notification |
| `OrderConfirmed` | order | notification |
| `OrderShipped` | order | notification |
| `OrderCancelled` | order | notification, catalog |
| `StockReserved` | catalog | order |
| `StockReleased` | catalog | — |

### 5.3 发件箱模式 — 发布流程

```
Aggregate.someAction()
    在内部注册 DomainEvent
    ↓
{Agg}PersistenceAdapter.save(aggregate)   ← @Transactional
    mapper.toNewEntity(aggregate)
      → BookEntityMapper / OrderEntityMapper 通过 attachDomainEvents()
        将领域事件附加到 AbstractAggregateRootEntity
    jpaRepository.save(entity)
      → Spring Data @DomainEvents 向上下文发布事件
        → OutboxWriteListener.beforeCommit(event)
             调用 OutboxMapper.toOutboxEntry(domainEvent)
             将 OutboxEventJpaEntity 写入 outbox_event 表
    @Transactional 提交（聚合 + 发件箱行在同一 ACID 事务中）
    ↓
OutboxRelayScheduler
    读取 PENDING 发件箱行
    将 Avro 字节发布到 Kafka
    将行标记为 PUBLISHED
```

关键不变式：**聚合状态变更和发件箱行始终在同一 ACID 事务中**。不存在其中一个存在而另一个不存在的窗口期。

### 5.4 OutboxMapper SPI

每个服务恰好实现一个 `OutboxMapper`（位于 `infrastructure/messaging/outbox/`）：

```java
// seedwork OutboxMapper SPI
public interface OutboxMapper {
    Optional<OutboxEntry> toOutboxEntry(DomainEvent event);
}

// CatalogOutboxMapper — 对事件类型进行模式匹配
@Component
public class CatalogOutboxMapper implements OutboxMapper {
    @Override
    public Optional<OutboxEntry> toOutboxEntry(DomainEvent event) {
        return switch (event) {
            case StockReserved e -> {
                var avro = com.example.events.v1.StockReserved.newBuilder()
                        .setEventId(e.eventId().toString())
                        // ... 字段映射
                        .build();
                yield Optional.of(new OutboxEntry(
                        e.eventId(), e.bookId(),
                        KafkaResourceConstants.TOPIC_STOCK_RESERVED,
                        avro.getClass().getName(),
                        avroSerializer.serialize(topic, avro)));
            }
            default -> Optional.empty();
        };
    }
}
```

### 5.5 Kafka 消费者幂等性

所有 Kafka 消费者必须是幂等的（消息可能被多次投递）。seedwork 基础设施透明地处理这一问题：

```
KafkaMessageProcessor.process(handler, record, ack)
    ├── SELECT FROM processed_events WHERE id = eventId
    │   └── 找到 → ack，返回（重复 — 跳过）
    │   └── 未找到 →
    │       handler.handle(event)           ← 业务逻辑
    │       INSERT INTO processed_events    ← 标记已处理
    │       ack                             ← 提交偏移量
    │
    └── 处理器失败时 →
        INSERT INTO consumer_retry_events   ← 存储以供重试
        ack                                 ← 解除 Kafka 分区阻塞
```

重试使用**基于数据库的指数退避**，配合声明模式，防止跨多个服务实例的重复处理。完整设计参见 [ADR-009](adr/ADR-009-kafka-consumer-idempotency-retry.md)。

---

## 第六部分：事务规则

### 6.1 默认：事务在持久化适配器上

对于常见情况 — 一个处理器、一个聚合 — `@Transactional` 位于持久化适配器的 `save()` 方法上：

```
PlaceOrderCommandHandler.handle():
  ① CatalogClient.checkStock()       ← 无事务
  ② CatalogClient.reserveStock()     ← 无事务
  ③ Order.create() + order.place()   ← 内存中，无事务
  ④ OrderPersistence.save(order)     ← @Transactional 在此开启并提交
                                        （发件箱写入包含在同一事务中）
  ⑤ 返回 PlaceOrderResult            ← 从内存中的状态组装，无事务
```

### 6.2 提交后副作用 — 决策矩阵

| 副作用类型 | 机制 | 示例 |
|-----------------|-----------|---------|
| **命令的主要目的** | 在处理器中 `save()` 后直接调用 | `SendNotificationCommandHandler` 调用 `EmailSender.send()` |
| **响应，同一服务，单一消费者** | `@TransactionalEventListener(AFTER_COMMIT)` | 书籍更新后的缓存失效 |
| **响应，同一服务，多个消费者** | 领域事件 + `@TransactionalEventListener` | 多个组件响应订单状态变更 |
| **跨服务响应** | 领域事件 → 通过发件箱的集成事件（强制） | `OrderCancelled` → 目录服务释放库存 |
| **基础设施操作（缓存、搜索索引）** | 在持久化适配器 `save()` 内部 | 缓存驱逐 — 处理器不感知 |

**缓存失效模式（catalog）：**

`BookPersistenceAdapter.save()` 在 JPA 保存后立即发布一个 `BookPersistedEvent`（Spring 应用事件，不是领域事件）。`BookCacheInvalidationListener` 使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 处理它，确保缓存驱逐仅在 DB 提交确认后发生。

```java
// 在 BookPersistenceAdapter.save() 内部
BookJpaEntity saved = bookJpaRepository.save(entity);
eventPublisher.publishEvent(new BookPersistedEvent(book.getId().value()));

// BookCacheInvalidationListener
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onBookPersisted(BookPersistedEvent event) {
    bookCache.evict(event.bookId());
}
```

### 6.3 同服务领域事件监听器分类

同服务领域事件监听器在架构中扮演两种截然不同的角色。正确放置它们需要理解六边形架构中主动适配器（driving）和被动适配器（driven）的区分。

**基础设施监听器（被动适配器）** 执行纯技术 I/O — 数据同步，不含业务逻辑。位于 `infrastructure/`：

| 监听器 | 位置 | 职责 |
|--------|------|------|
| `OutboxWriteListener` | `seedwork/.../infrastructure/outbox/` | 序列化领域事件 → 写入 outbox 行（BEFORE_COMMIT） |
| `BookCacheInvalidationListener` | `catalog/.../infrastructure/cache/` | 失效 Redis 缓存条目（AFTER_COMMIT） |

注意：order 服务的 ES 读模型同步由外部 Debezium CDC → ES Sink Connector 管道处理，不是应用内监听器。

**业务事件监听器（主动适配器）** 将领域事件翻译为 Command，通过 CommandBus 分发。不含任何业务逻辑 — 决策在 CommandHandler 和领域模型中。结构上与 REST Controller 和 Kafka Consumer 完全对称：

| 主动适配器 | 触发源 | 动作 |
|-----------|--------|------|
| REST Controller | HTTP 请求 | `commandBus.dispatch(Command)` |
| Kafka Consumer | Kafka 消息 | `commandBus.dispatch(Command)` |
| **事件监听器** | **领域事件** | `commandBus.dispatch(Command)` |

业务事件监听器位于 `interfaces/event/`：

| 监听器 | 位置 | 分发的 Command |
|--------|------|---------------|
| `OrderPlacedAutoConfirmListener` | `order/.../interfaces/event/` | 通过 CommandBus 分发 `AutoConfirmOrderCommand` |

**判断标准：** 监听器是驱动应用层执行业务逻辑，还是自己在做技术 I/O？
- 驱动应用层 → `interfaces/event/`（主动适配器）
- 执行 I/O → `infrastructure/`（被动适配器）

### 6.4 永不在事务中包裹外部 I/O

在 HTTP 调用或发送邮件期间持有 DB 连接：
- 在高负载下耗尽连接池。
- 使回滚在语义上不正确（回滚无法撤销已发送的邮件或已成功的 HTTP 调用）。

`@Transactional` 在 `save()` 上意味着处理器进行任何保存后调用之前，事务已经提交。

### 6.5 永不在领域服务上使用事务注解

领域服务上的 `@Transactional` 会创建一个 Spring 代理，并对 `PlatformTransactionManager` 添加隐藏依赖。领域服务将无法在没有 Spring 上下文的情况下进行测试。

### 6.6 跨聚合原子性（同一数据库）

如果单个用例必须原子性地将两个聚合根保存到同一数据库，将 `@Transactional` 放在 CommandHandler 上。这是一个架构例外 — 需在 ADR 中记录。首先验证这两个聚合实际上不是同一个聚合。

### 6.7 跨服务原子性

在数据库层面不可能实现。使用 Saga 模式：每个步骤是一个独立的原子提交；失败通过作为事件发布的补偿事务处理。

```
PlaceOrderHandler:
  保存 Order(PENDING) + outbox(OrderPlaced)     ← 原子提交

catalog 消费 OrderPlaced:
  预留库存 → 发出 StockReserved 或 StockReservationFailed

order 消费 StockReserved:
  确认订单                                      ← 原子提交

order 消费 StockReservationFailed:
  取消订单                                      ← 补偿事务
```

---

## 第七部分：命名约定

| 概念 | 位置 | 模式 | 示例 |
|---------|----------|---------|---------|
| 命令 | `application/command/{agg}/` | `{Action}{Agg}Command` | `PlaceOrderCommand` |
| 命令处理器 | `application/command/{agg}/` | `{Action}{Agg}CommandHandler` | `PlaceOrderCommandHandler` |
| 命令结果 | `application/command/{agg}/` | `{Action}{Agg}Result` | `PlaceOrderResult` |
| 查询 | `application/query/{agg}/` | `{Criteria}{Agg}Query` | `GetOrderQuery`, `ListOrdersQuery` |
| 查询处理器 | `application/query/{agg}/` | `{Criteria}{Agg}QueryHandler` | `GetOrderQueryHandler` |
| 读模型结果 | `application/query/{agg}/` | `{Agg}{Purpose}Result` | `OrderDetailResult`, `OrderSummaryResult` |
| 写侧端口 | `domain/ports/` | `{Agg}Persistence` | `OrderPersistence`, `BookPersistence` |
| 读侧端口 | `application/port/outbound/` | `{Agg}SearchRepository` | `OrderSearchRepository` |
| 读取备用端口 | `application/port/outbound/` | `{Agg}ReadRepository` | `OrderReadRepository` |
| 缓存端口 | `application/port/outbound/` | `{Agg}Cache` | `BookCache` |
| HTTP 客户端端口 | `application/port/outbound/` | `{Target}Client` | `CatalogClient` |
| 领域事件 | `domain/event/` | `{Agg}{PastTense}` | `OrderPlaced`, `StockReserved` |
| JPA 实体 | `infrastructure/repository/jpa/` | `{Agg}JpaEntity` | `OrderJpaEntity` |
| JPA 仓储 | `infrastructure/repository/jpa/` | `{Agg}JpaRepository` | `OrderJpaRepository` |
| 持久化适配器 | `infrastructure/repository/jpa/` | `{Agg}PersistenceAdapter` | `BookPersistenceAdapter` |
| 实体映射器 | `infrastructure/repository/jpa/` | `{Agg}EntityMapper` | `BookEntityMapper` |
| ES 文档 | `infrastructure/repository/elasticsearch/` | `{Agg}ElasticDocument` | `OrderElasticDocument` |
| ES 仓储 | `infrastructure/repository/elasticsearch/` | `{Agg}ElasticRepository` | `OrderElasticRepository` |
| ES 适配器 | `infrastructure/repository/elasticsearch/` | `{Agg}SearchAdapter` | `OrderSearchAdapter` |
| 发件箱映射器 | `infrastructure/messaging/outbox/` | `{Service}OutboxMapper` | `CatalogOutboxMapper` |
| HTTP 客户端适配器 | `infrastructure/client/` | `{Target}RestClient` | `CatalogRestClient` |
| 缓存适配器 | `infrastructure/cache/` | `{Technology}{Agg}Cache` | `RedisBookCache` |
| REST 控制器（写） | `interfaces/rest/` | `{Agg}CommandController` | `OrderCommandController` |
| REST 控制器（读） | `interfaces/rest/` | `{Agg}QueryController` | `OrderQueryController` |
| HTTP 请求 DTO | `interfaces/rest/request/` | `{Action}{Agg}Request` | `PlaceOrderRequest` |
| HTTP 响应 DTO | `interfaces/rest/response/` | `{Agg}{Purpose}Response` | `OrderDetailResponse`, `PlaceOrderResponse` |
| Kafka 消费者 | `interfaces/messaging/consumer/` | `{Topic}EventConsumer` | `OrderEventConsumer` |
| Kafka 事件处理器 | `interfaces/messaging/consumer/` | `{Event}Handler` | `OrderPlacedHandler` |
| 同服务事件监听器（业务） | `interfaces/event/` | `{Event}{Reaction}Listener` | `OrderPlacedAutoConfirmListener` |
| 同服务事件监听器（技术） | `infrastructure/{concern}/` | `{Aggregate}{Concern}Listener` | `BookCacheInvalidationListener` |

---

## 第八部分：测试策略

```
单元测试      → 领域层 + 应用层（无 Spring 上下文，无 Docker）
集成测试      → 每个适配器独立测试（Testcontainers：Postgres、Redis、Kafka、ES）
组件测试      → 全服务内存测试（SpringBootTest + Testcontainers）
契约测试      → 通过 PactFlow 的 REST API BDCT（order-service → catalog-service）
E2E 测试      → 针对预部署环境的 REST 调用（CI 中无基础设施配置）
```

### 8.1 按层划分

| 区域 | 测试风格 | 配置 | 速度 |
|------|-----------|-------|-------|
| 领域模型 | JUnit 5，无 mock — 通过行为测试 | 无 | < 1ms |
| 领域服务 | JUnit 5，传入内存实体 | 无 | < 1ms |
| 命令/查询处理器 | JUnit 5 + Mockito 用于端口 mock | 仅 Mockito | < 100ms |
| 持久化适配器 | `@DataJpaTest` + Testcontainers PostgreSQL | Docker | < 1s |
| REST 控制器 | `@WebMvcTest` + MockMvc（mock 总线） | Spring 切片 | < 500ms |
| Kafka 消费者 | Testcontainers Kafka | Docker | < 2s |
| 完整服务 | `@SpringBootTest` + 所有 Testcontainers | Docker | < 10s |

### 8.2 领域测试 — 零 Mock

```java
// 无 Spring，无 Mockito，无 Docker
@Test
void order_cannot_be_cancelled_after_shipping() {
    var order = Order.create(customerId, items);
    order.place();
    order.confirm();
    order.ship("TRACK-123");

    assertThatThrownBy(() -> order.cancel("changed mind"))
        .isInstanceOf(OrderStateException.class);
}
```

### 8.3 处理器测试 — 仅 Mock 端口

```java
// 无 Spring 上下文，无 Docker
@ExtendWith(MockitoExtension.class)
class PlaceOrderCommandHandlerTest {
    @Mock OrderPersistence orderPersistence;
    @Mock CatalogClient catalogClient;
    @InjectMocks PlaceOrderCommandHandler handler;

    @Test
    void places_order_successfully() { ... }
}
```

### 8.4 契约测试（BDCT）

范围：`order-service`（消费者）→ `catalog-service`（提供者）

- 消费者编写 Pact 测试 → 生成 `build/pacts/order-service-catalog-service.json` → 发布到 PactFlow。
- 提供者发布 OAS 规范（由 springdoc-openapi 从实际 `@RestController` 注解生成）→ PactFlow 交叉验证。
- 无提供者侧重放测试。PactFlow `can-i-deploy` 门控阻止不兼容的部署。

```bash
# 本地运行消费者契约测试（无需 Testcontainers）
cd order && ./gradlew test --tests "com.example.order.contract.*"
```

完整 CI 工作流参见 [ADR-011](adr/ADR-011-swaggerhub-pactflow-bdct.md)。

---

## 第九部分：反模式

### 领域模型

| 反模式 | 症状 | 修复 |
|-------------|---------|-----|
| 领域聚合上有 `@Entity` | 聚合带有 JPA 注解 | 在 `infrastructure/` 中创建独立的 `*JpaEntity`；显式映射 |
| 领域服务上有 `@Transactional` | 领域服务没有 Spring 无法测试 | 移除；事务范围属于处理器或适配器 |
| 仓储注入到领域服务 | 领域服务构造器接受端口 | 将获取移到处理器；以参数形式传入实体 |
| JPA 实体中有业务逻辑 | `@Entity` 类包含 `if/else` 业务规则 | 移至聚合；JPA 实体仅是持久化映射结构 |

### 应用层

| 反模式 | 症状 | 修复 |
|-------------|---------|-----|
| 处理器返回查询 DTO | `PlaceOrderCommandHandler` 返回 `OrderDetailResponse` | 返回从内存中的领域状态组装的 `PlaceOrderResult` |
| 处理器中有业务逻辑 | 处理器包含 `if (businessCondition) throw …` | 将检查移到领域方法或领域服务 |
| 控制器中直接注入处理器 | `@Autowired PlaceOrderCommandHandler handler` | 仅通过 `CommandBus` 分发 |
| 应用包中有 JPA/Kafka 导入 | 处理器中有 `import jakarta.persistence.*` | 将任何持久化/消息代码移到 `infrastructure/` |
| 处理器旁边有应用服务 | `application/` 中有命名为 `*Service` 的类（非领域服务） | 合并到处理器中；只有领域服务使用 `*Service` 命名 |

### 基础设施

| 反模式 | 症状 | 修复 |
|-------------|---------|-----|
| JPA 实体暴露在 `jpa/` 包之外 | 处理器或控制器引用 `*JpaEntity` | 在适配器内映射为领域对象；使用包私有类 |
| 适配器有 `@Transactional` 时处理器也有 | 双重事务管理 | 从处理器移除；保留在适配器 `save()` 上 |
| 事务内有外部 I/O | HTTP 调用或发送邮件时持有 DB 连接 | 将外部调用移到 `save()` 调用之前或之后 |
| 处理器中有缓存管理 | 处理器调用 `bookCache.put()` | 将缓存放入/驱逐移到持久化适配器的 `save()` 中 |
| 处理器中调用 EventDispatcher | 处理器中有 `eventPublisher.publish(new OrderPlaced(…))` | 移除；发布是通过 `OutboxWriteListener` 的 `persistence.save()` 的副作用 |

### 事件与跨服务

| 反模式 | 症状 | 修复 |
|-------------|---------|-----|
| 领域事件导入 Avro 类 | `domain/event/OrderPlaced.java` 导入 `com.example.events.v1.*` | 领域事件是纯 Java 记录；只有 `OutboxMapper` 导入 Avro |
| 跨服务状态变更使用 HTTP 调用 | 取消订单后调用 `catalogClient.releaseStock()` | 使用 `OrderCancelled` 集成事件；catalog 服务消费并幂等地释放库存 |
| 跨服务共享数据库表 | 两个服务查询同一张表 | 每个服务拥有其数据库；跨服务数据通过 Kafka 事件流动 |
| 写侧端口在 `application/port/outbound/` 中 | `OrderPersistence` 与 `CatalogClient` 放在一起 | 写侧端口使用领域语言 → `domain/ports/`；只有基础设施抽象放入 `application/port/outbound/` |
| 消费者没有幂等性检查 | Kafka 监听器没有去重 | 使用来自 seedwork 的 `KafkaMessageProcessor` 或 `IdempotentKafkaListener` |

---

## 第十部分：技术决策（ADR 索引）

| ADR | 决策 | 理由 |
|-----|----------|-----------|
| [ADR-001](adr/ADR-001-explicit-architecture-over-layered.md) | 采用显式架构而非传统分层架构 | 可测试性、强制边界、框架独立性 |
| [ADR-002](adr/ADR-002-cqrs-scope-order-service.md) | CQRS 仅应用于 `order`（PostgreSQL 写 + Elasticsearch 读） | Order 有复杂读取模式；catalog 和 notification 不值得增加开销 |
| [ADR-003](adr/ADR-003-event-schema-ownership.md) | `shared-events` 作为 Avro SDK（模式 → 生成类 → mavenLocal） | 编译时契约；Schema Registry 强制向后兼容性 |
| [ADR-005](adr/ADR-005-outbox-pattern.md) | 发件箱模式用于保证事件投递 | 聚合 + 事件行在同一 ACID 事务中原子性；无需分布式事务 |
| [ADR-006](adr/ADR-006-database-per-service.md) | 每服务独立数据库，无共享表 | 限界上下文隔离；独立部署和扩缩 |
| [ADR-007](adr/ADR-007-java21-virtual-threads.md) | Java 21 + 虚拟线程 + 现代语言特性 | 记录类用于值对象/事件/DTO；密封接口用于领域枚举；模式匹配用于领域逻辑 |
| [ADR-008](adr/ADR-008-shared-events-versioning.md) | `shared-events` 使用语义化版本；仅向后兼容模式变更；破坏性变更使用 `v2/` 命名空间 | 防止消费者因模式演进而静默损坏 |
| [ADR-009](adr/ADR-009-kafka-consumer-idempotency-retry.md) | 基于数据库的幂等性（`processed_events`）+ 声明模式重试（`consumer_retry_events`） | 永不阻塞 Kafka 分区；Pod 重启后可恢复；无需外部锁管理器的分布式安全 |
| [ADR-010](adr/ADR-010-opentelemetry-observability.md) | 通过 Kubernetes Operator 的 OpenTelemetry 用于链路追踪 + Prometheus 用于指标 | 统一的可观测性，无需每服务单独配置 Agent |
| [ADR-011](adr/ADR-011-swaggerhub-pactflow-bdct.md) | SwaggerHub（API 注册中心）+ PactFlow BDCT（契约测试） | 提供者团队不被消费者 pact 状态阻塞；OAS 从实际控制器代码生成 |

### 模块构建顺序

```bash
# 始终按此顺序构建（每个步骤发布到 mavenLocal）
cd seedwork      && ./gradlew publishToMavenLocal
cd shared-events && ./gradlew publishToMavenLocal
# 现在可以构建任意服务
cd catalog / order / notification
```

### 关键技术栈

| 关注点 | 技术 |
|---------|-----------|
| 语言 | Java 21（启用预览特性） |
| 框架 | Spring Boot 3 |
| 构建工具 | Gradle（每服务独立项目，无多项目根） |
| 容器镜像 | Jib（无 Dockerfile；基础镜像：`eclipse-temurin:21-jre-alpine`） |
| 写数据库 | PostgreSQL（每服务独立） |
| 缓存 | Redis（仅 catalog） |
| 搜索 | Elasticsearch（order 读模型） |
| 消息 | Kafka + Confluent Avro + Schema Registry |
| 事件中继 | Debezium CDC（生产环境）/ `OutboxRelayScheduler`（本地开发） |
| 可观测性 | OpenTelemetry + Prometheus + Jaeger + Grafana |
| 服务网格 | Istio（mTLS、入口） |
| 邮件 | `LogEmailAdapter`（日志模拟；无 SMTP） |
| 虚拟线程 | `spring.threads.virtual.enabled=true` |

---

## 附录：新增功能检查清单

1. 在 `domain/model/` 中定义领域模型变更
2. 在 `domain/event/` 中新增/更新领域事件
3. 如需持久化：在 `domain/ports/` 中定义或更新写侧端口
4. 如需外部客户端/缓存/搜索：在 `application/port/outbound/` 中定义端口
5. 在 `application/command/{agg}/` 中添加 `{Action}{Agg}Command` + `{Action}{Agg}Result` + `@Service` CommandHandler
6. 在 `application/query/{agg}/` 中添加 `{Criteria}{Agg}Query` + `@Service` QueryHandler + 响应 DTO
7. 在 `infrastructure/repository/jpa/` 中实现持久化适配器 — 在此包含缓存失效逻辑
8. 在 `interfaces/rest/` 中添加 REST 端点 — 通过 `CommandBus`/`QueryBus` 分发
9. 如有模式变更，添加 Flyway 迁移
10. 编写领域行为测试（JUnit 5，无 mock）
11. 编写处理器单元测试（Mockito mock 端口）
12. 编写持久化适配器集成测试（Testcontainers）
