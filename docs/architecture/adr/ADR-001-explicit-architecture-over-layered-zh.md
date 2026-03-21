# ADR-001: 采用显式架构而非传统分层架构

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team
- **Reference**: [Herberto Graça — Explicit Architecture](https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/)

---

## 背景

网上书店系统涵盖不同的业务领域（目录、订单、通知），各领域在扩展性、持久化需求和团队归属上均有差异。我们需要一种满足以下条件的架构：

- 保持业务逻辑独立于框架和基础设施选型
- 允许在不修改业务规则的前提下替换基础设施（数据库、缓存、消息队列）
- 仅凭包结构即可清晰表达系统意图
- 在每一层均支持可测试性，无需启动完整的 Spring 上下文

传统的**分层架构**（`controller → service → repository`）是 Spring Boot 项目的默认选择，但它存在以下问题：

1. **框架泄漏**：`@Transactional`、JPA 注解以及 Spring 构造型注解悄然渗入"service"层，导致业务逻辑在没有 Spring 上下文的情况下无法测试。
2. **无强制边界**：没有任何机制阻止 controller 直接调用 repository，或 service 导入另一个 service 的内部类。
3. **技术耦合**：从 JPA 切换到 JDBC，或新增 Redis 缓存层，都需要修改 service 层。
4. **意图隐晦**：一个名为 `service/` 的包对应用实际做什么毫无说明。

---

## 决策

我们采用**显式架构**——DDD、六边形架构（端口与适配器）、洋葱架构和整洁架构的综合体——作为每个微服务的结构模式。

应用核心分为两层：

### 领域层（最内层）

包含业务的**是什么**：实体、聚合、值对象、领域事件和领域服务。该层对任何框架、库或其他层**零依赖**，是纯 Java 实现。

### 应用层

包含业务用例的**如何做**：应用服务实现用例接口（主端口），并依赖 repository/消息接口（次端口）。该层仅依赖领域层，用例类上不添加 Spring 注解。

### 接口层（最外层——驱动侧）

包含*主（驱动）适配器*：REST 控制器和入站 Kafka 消费者。它们接收外部请求，并将其转换为应用层的 Command 或 Query。Spring MVC / 消息注解位于此处。

### 基础设施层（最外层——被驱动侧）

包含*次（被驱动）适配器*：JPA repository、Kafka 生产者、Redis 客户端、ElasticSearch 适配器、出站 HTTP 客户端。应用层通过次端口调用它们。所有框架和 I/O 代码位于此处。

**依赖规则**：依赖关系始终指向内层。领域层对应用层一无所知，应用层对基础设施层一无所知。违规行为由 ArchUnit 测试在构建时检测。

---

## 包结构

```
com.example.{service}/
├── domain/           ← 零依赖：纯 Java
├── application/      ← 仅依赖 domain；不导入任何框架
├── infrastructure/   ← 被驱动适配器：JPA、Kafka 生产者、Redis、HTTP 客户端
└── interfaces/       ← 驱动适配器：REST 控制器、Kafka 消费者
```

`interfaces/` 和 `infrastructure/` 同属外层，均依赖 `application/`。二者保持分离，是因为它们在端口与适配器模型中扮演相反的角色——驱动侧与被驱动侧。

---

## 影响

### 积极影响
- **可测试性**：领域层和应用层可用纯 JUnit 5 测试，无需 Spring 上下文，无需 Docker。测试套件可在数秒内完成。
- **框架无关性**：从 Spring Boot 切换到 Quarkus，或从 JPA 切换到 JOOQ，变更仅限于 `infrastructure/` 包。
- **意图显式**：包名（`command/`、`query/`、`port/out/`、`interfaces/`、`infrastructure/`）传达的是架构角色，而非技术分层。
- **边界强制**：ArchUnit 规则在构建时捕获跨层的意外导入。
- **团队可扩展性**：清晰的归属边界便于将团队分配到各层或各限界上下文。

### 消极影响
- **初期样板代码较多**：与普通分层架构相比，需要更多接口、映射器和包。
- **学习曲线**：不熟悉六边形架构的开发者需要一定的上手时间。
- **间接性**：端到端追踪一个功能需要浏览更多文件（controller → port/in → usecase → port/out → persistence adapter）。

### 缓解措施
- CLAUDE.md 明确记录了相关规范。
- ADR-001（本文档）解释了决策动机，帮助开发者理解"为什么"。
- `simplify` 技巧对架构结构性代码禁用——此处的样板代码是有意为之的。
