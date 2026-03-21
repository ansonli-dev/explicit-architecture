# Clarified Architecture（澄明架构）

**基于 DDD / Hexagonal / Onion / Clean / CQRS 的务实精炼方案**

> 版本 1.0 · 2026 年 3 月

---

## 目录

- [第一部分：哲学与原则](#第一部分哲学与原则)
  - [1.1 为什么叫"澄明"？](#11-为什么叫澄明)
  - [1.2 唯一不可妥协的规则](#12-唯一不可妥协的规则)
  - [1.3 指导原则](#13-指导原则)
- [第二部分：结构总览](#第二部分结构总览)
  - [2.1 四个区域](#21-四个区域)
  - [2.2 依赖方向](#22-依赖方向)
  - [2.3 端口与适配器的保留](#23-端口与适配器的保留)
- [第三部分：解决五个张力点](#第三部分解决五个张力点)
  - [3.1 张力 1：用例只有一个家](#31-张力-1用例只有一个家)
  - [3.2 张力 2：领域服务的纯净性](#32-张力-2领域服务的纯净性)
  - [3.3 张力 3：仓储接口归属与 CQRS 读路径](#33-张力-3仓储接口归属与-cqrs-读路径)
  - [3.4 张力 4：共享内核的最小化](#34-张力-4共享内核的最小化)
  - [3.5 张力 5：跨组件数据一致性](#35-张力-5跨组件数据一致性)
- [第四部分：组件设计](#第四部分组件设计)
  - [4.1 什么是组件？](#41-什么是组件)
  - [4.2 组件间通信规则](#42-组件间通信规则)
  - [4.3 组件内部结构（推荐目录布局）](#43-组件内部结构推荐目录布局)
- [第五部分：控制流](#第五部分控制流)
  - [5.1 命令流（写路径）](#51-命令流写路径)
  - [5.2 查询流（读路径）](#52-查询流读路径)
  - [5.3 跨组件事件流](#53-跨组件事件流)
  - [5.4 事务边界规则](#54-事务边界规则)
- [第六部分：按项目规模的分级指南](#第六部分按项目规模的分级指南)
- [第七部分：执行与测试策略](#第七部分执行与测试策略)
  - [7.1 架构适应度函数](#71-架构适应度函数)
  - [7.2 按区域的测试策略](#72-按区域的测试策略)
- [第八部分：应避免的反模式](#第八部分应避免的反模式)
- [第九部分：架构决策记录](#第九部分架构决策记录)
- [第十部分：总结](#第十部分总结)

---

## 第一部分：哲学与原则

### 1.1 为什么叫"澄明"？

Herberto Graça 的 Explicit Architecture 是一份杰出的概念地图，将 DDD、六边形架构、洋葱架构、整洁架构和 CQRS 融合为一个统一的心智模型，已成为业界引用最多的架构文章之一。然而，当团队尝试将其落地时，他们始终会在五个张力点上遭遇困境——原文将决策权留给了读者：

1. **Application Service 与 Command Handler** — 同一职责的两个容器。
2. **Domain Service 与 Application Service** — 取决于谁来界定的边界。
3. **Repository 接口的归属** — Application 层还是 Domain 层？
4. **Shared Kernel 的膨胀** — 吞噬所有组件的万有引力中心。
5. **跨组件数据一致性** — 没有安全网的最终一致性。

**Clarified Architecture（澄明架构）** 用一组有主见的、默认安全的选择来解决上述每个张力点。名称本身反映了意图：**我们不是在发明新架构，而是在澄清一个已有的、经过验证的架构中的模糊接缝。**

### 1.2 唯一不可妥协的规则

> **铁律**
>
> Domain Model 不依赖自身之外的任何东西。没有基础设施接口，没有应用层类型。这是在所有项目规模、团队结构和部署拓扑中都必须守住的唯一不变量。其余所有架构边界的存在都是为了保护这一条。

本文档中的其他一切都是"可根据项目上下文放宽"的默认值。但放宽铁律永远是错误的。如果你的 Entity 导入了 Repository，那么无论文件夹结构声称有多少层，你的架构都已经坍塌了。

> **关于框架注解**
>
> 铁律禁止在 Domain Model 和 Domain Services 区域使用**有运行时行为的框架注解**：`@Entity`、`@Transactional`、`@Lazy`、JPA 关联注解（`@OneToMany` 等），以及任何会改变运行时行为或创建框架托管代理的注解。这些注解会改变对象的构建、加载和刷新方式——这些都是基础设施关注点，会污染领域语义。
>
> **纯粹的发现注解**（`@Component`、`@Service`）在单一框架项目中用于 Domain Services 是可以接受的。它们不携带任何运行时行为，不影响可测试性（有没有这些注解，`new OrderPricingService()` 的行为完全相同），仅用于告知容器如何装配。如果项目需要跨框架移植或模块严格隔离，这些注解同样应当移除，将装配职责交给 Application 层。

### 1.3 指导原则

1. **保护内圈，放宽外圈。** Domain Model 是神圣的。Application 层是重要的。Infrastructure 层是可替换的。按此优先级分配你的架构纪律。
2. **每个概念只有一个家。** 如果两个构造服务于相同的目的，消灭其中一个。放置上的歧义比不完美的放置更具破坏力。
3. **架构复杂度 ≤ 问题复杂度。** 如果你的架构活动部件比它所解决的业务问题还多，你就过度工程化了。
4. **按需添加接缝。** 从保持铁律的最简安排开始。只在具体需求浮现时才引入间接层（Bus、事件溯源、CQRS）。
5. **可测试性即证明。** 如果某一层在不 mock 基础设施的情况下很难做单元测试，说明边界画在了错误的位置。

---

## 第二部分：结构总览

### 2.1 四个区域

澄明架构保留了洋葱架构的同心圆模型，但将其简化为四个无歧义的区域（由内到外）：

| 区域 | 包含 | 依赖于 | 关键约束 |
|------|------|--------|----------|
| **Domain Model** | 实体、值对象、领域事件、规约、枚举 | 无 | 不导入基础设施/应用层；不使用有运行时行为的框架注解 |
| **Domain Services** | 纯粹的跨实体业务逻辑 | 仅 Domain Model | 无 I/O，无副作用；不使用有运行时行为的注解（`@Transactional` 等） |
| **Application（用例）** | Handler、端口、DTO、应用事件、ReadAuthorization | Domain Model + Domain Services | 仅做编排；不含业务规则 |
| **Infrastructure + UI** | 适配器、控制器、ORM 配置、消息中间件、外部 API | 所有内层区域（通过端口） | 实现端口；永远不被内层区域导入 |

> **与 Explicit Architecture 的差异**
>
> Graça 的原始模型将 Domain Services 和 Domain Model 放在同一层。我们将它们拆分为两个区域，因为对 Domain Services 施加"无 I/O"这一约束，是对可测试性和可维护性影响最大的单一边界，值得成为一条显式的、可强制执行的规则，而非一个指导方针。

### 2.2 依赖方向

所有源代码依赖指向内圈。这意味着：

- Infrastructure 依赖 Application（实现端口）。
- Application 依赖 Domain Services 和 Domain Model（调用纯逻辑，引用实体）。
- Domain Services 依赖 Domain Model（操作实体和值对象）。
- Domain Model 不依赖任何东西。

在运行时，流向反转：Controller（外层）调用 Handler（应用层），Handler 将预加载的实体传给 Domain Service（内层）做纯逻辑运算，然后使用通过端口注入的 Repository 适配器（外层）持久化变更。

### 2.3 端口与适配器的保留

澄明架构完整保留了端口与适配器（六边形）模型：

- **端口（Ports）** 是定义在 Application 区域内的接口。它们用应用自身的语言描述应用需要外部世界提供什么（持久化、消息、搜索、通知）。
- **驱动适配器（Driving Adapters）**（Controller、CLI 命令、消息消费者）位于 UI/Infrastructure 区域，将外部输入翻译为端口调用。
- **被驱动适配器（Driven Adapters）**（Repository 实现、API 客户端、邮件发送器）实现端口，通过依赖注入被注入到 Handler 中。

> **端口设计规则**
>
> 端口应针对应用核心的需求来设计，绝不能去镜像外部工具的 API。一个名为 `EmailSender` 的端口，拥有方法 `sendWelcomeEmail(UserId)` 是正确的。一个暴露裸 SMTP 概念（`setHeader`、`setBody`、`connect`）的端口是抽象泄漏。

---

## 第三部分：解决五个张力点

### 3.1 张力 1：用例只有一个家

**问题：** Explicit Architecture 允许用例逻辑同时存在于 Application Service 或 Command Handler 中，为同一职责创造了两个可能的"家"。团队最终陷入放置不一致和决策成本浪费的困境。

**澄明决策：选择一个容器，在项目范围内强制执行。**

| 如果你有… | 则使用… | 消灭… |
|-----------|---------|-------|
| Command/Query Bus | Handler 作为唯一用例容器 | Application Service 这一概念 |
| 无 Bus（简单项目） | Application Service 作为唯一用例容器 | Handler 这一概念 |

不存在新代码中两者应共存的场景。如果你正在迁移一个已使用 Application Service 的遗留系统并引入 Bus，共存是一个临时状态——需要在 ADR 中记录明确的迁移截止日期。

**Handler 的解剖（使用 Bus 时）：**

1. 接收一个 Command 或 Query 对象。
2. 通过 Repository 端口加载所需实体。
3. 将纯业务逻辑委托给 Domain Model 或 Domain Services。
4. 通过 Repository 端口持久化结果。
5. 派发应用事件。

如果两个 Handler 共享逻辑，将其提取为同一 Application 区域内的私有方法或共享用例片段。不要重新引入 Application Service 作为一个类。

> **执行保障**
>
> 使用静态分析工具（Java/Kotlin 的 ArchUnit、PHP 的 Deptrac、TypeScript 的 dependency-cruiser）来强制：如果存在 Bus，Application 区域内不允许命名为 `*Service` 的类；如果没有 Bus，不允许命名为 `*Handler` 的类。

### 3.2 张力 2：领域服务的纯净性

**问题：** Domain Service 与 Application Service 的边界在 Domain Service 需要获取数据（但它不能依赖 Repository）时变得模糊。这导致层之间的"乒乓式"调用。

**澄明决策：Domain Services 是纯函数。它们将所有输入作为参数接收并返回结果。它们永远不持有 Repository 引用，永远不触发 I/O，永远不派发事件。**

> **Domain Service 契约**
>
> Domain Service 的方法签名必须看起来像这样：`domainOperation(Entity a, Entity b, ValueObject rate) → DomainResult`。如果你在 Domain Service 的构造函数中看到 Repository 或 Port，它应该属于 Handler。

**预取全部模式（Prefetch-All Pattern）：**

Handler 负责所有 I/O。它加载 Domain Service 需要的每个实体和值对象，传入，接收结果，然后持久化/派发。

| 步骤 | 负责区域 | 动作 |
|------|----------|------|
| 1 | Handler（Application） | 解析命令，从 Repository 加载 AccountA、AccountB、ExchangeRate |
| 2 | Domain Service | `transfer(accountA, accountB, rate) → TransferResult`（纯计算） |
| 3 | Handler（Application） | 持久化更新后的账户，派发 TransferCompleted 事件 |

**如果 Domain Service 有条件地需要数据怎么办？**

如果步骤 2 揭示需要额外数据（例如，超过 $10,000 的转账需要合规检查），Handler 应分为两个阶段：

1. **阶段 1：** Handler 加载初始实体 → Domain Service 计算并返回中间结果（如 `RequiresComplianceCheck`）。
2. **阶段 2：** Handler 加载合规数据 → Domain Service 完成操作。

这不是变通方案；这是正确的关注点分离。I/O 决策在 Handler 中。纯逻辑在 Domain Service 中。阶段边界使这一点显式化。

> **测试收益**
>
> 纯 Domain Services 以零 mock 方式测试：`new TransferService().transfer(fakeA, fakeB, fakeRate)`。测试执行即时、确定性、完全隔离于基础设施。

### 3.3 张力 3：仓储接口归属与 CQRS 读路径

**问题：** Graça 将 Repository 接口与 ORM 接口、消息接口一起放在 Application 层。这混淆了两件事：Repository（领域概念）和持久化抽象（基础设施关注点）。此外，CQRS 读路径完全绕过 Domain 层，没有为数据访问策略留下清晰的位置。

**澄明决策：将写路径和读路径干净地分开，将每个接口放在其语义归属之处。**

**写路径（Command 侧）：**

- **Repository 接口属于 Domain Model 区域。** `OrderRepository` 是一个领域概念（"所有订单的集合"）。它说的是领域的语言：`findByCustomer(CustomerId)`、`save(Order)`。
- **Repository 实现** 属于 Infrastructure 区域。它们使用 ORM、裸 SQL 或任何持久化技术来实现领域接口。
- **没有双层抽象。** 不存在单独的"持久化接口"来包装 ORM。一个接口（Repository）和一个实现（如 `MySqlOrderRepository`）对 95% 的项目来说就足够了。只有在你有切换 ORM 的具体计划时才添加 ORM 抽象层——而非作为投机性预防。

**读路径（Query 侧）：**

- **Query 对象存在于 Application 区域。** 它们绕过 Domain Model 直接查询数据库，返回 DTO。
- **ReadAuthorization 接口** 位于 Application 区域，作为 Query 执行前的薄门卫层。它执行"谁能看什么"的策略，但不涉及领域实体。
- **Query → DTO → ViewModel → View。** ViewModel 可以包含展示逻辑（格式化日期、计算显示标签）但不包含业务规则。

| 路径 | 接口位置 | 实现位置 | 是否经过 Domain？ |
|------|----------|----------|-------------------|
| 写（Command） | Domain Model 区域 | Infrastructure 区域 | 是 — 实体强制执行不变量 |
| 读（Query） | Application 区域 | Infrastructure 区域 | 否 — 直接 DB 查询到 DTO |

> **ReadAuthorization 契约**
>
> ReadAuthorization 不是一个完整的 ACL 系统。它是一个单方法接口：`canAccess(QueryContext, ResourceScope) → Boolean`。它由 Handler 在执行 Query 之前调用。如果失败，Handler 抛出授权异常。Query 本身对权限一无所知。

### 3.4 张力 4：共享内核的最小化

**问题：** Explicit Architecture 中的 Shared Kernel 成为引力中心。每个跨组件通信需求都往里添加事件类、规约对象和共享值对象。随着时间推移，它变成最大且最不稳定的模块，间接耦合了所有组件。

**澄明决策：用事件注册表（Event Registry）替代共享代码库——一个仅包含 schema、不含可执行代码的产物。**

**三条规则治理事件注册表：**

1. **事件所有权归属于发布者。** `OrderPlaced` 事件类只存在于 Order 组件内部。其他任何组件都不能直接导入它。
2. **注册表包含 schema，而非类。** 每个事件由名称和类型化字段列表描述（JSON Schema、Protobuf 或 Avro）。注册表在源代码仓库中与代码一起版本化。
3. **消费者构建自己的 DTO。** Billing 组件接收原始 `OrderPlaced` 消息，并将其反序列化为自己定义的 `BillingOrderPlacedDTO`，其中只包含 Billing 关心的字段。这是反腐败层（Anti-Corruption Layer）模式在事件边界上的应用。

**Schema 演进治理：**

- **仅允许向后兼容的变更：** 添加新可选字段是允许的。删除或重命名字段需要新的事件版本（如 `OrderPlacedV2`）。
- **CI 强制的兼容性检查：** 构建流水线中的 schema 注册表验证步骤会拒绝任何会破坏现有消费者的变更。
- **弃用生命周期：** 当新事件版本发布时，旧版本进入弃用窗口期（如 90 天）。窗口期结束后，旧 schema 被移除，消费者必须已完成迁移。

| 方面 | Graça 的 Shared Kernel | 澄明的 Event Registry |
|------|------------------------|----------------------|
| 包含 | 事件类、规约、共享值对象 | 仅事件 schema（名称 + 类型化字段） |
| 可执行代码 | 是 | 否 |
| 变更影响 | 所有组件重新编译 | 仅当 schema 契约被破坏时 |
| 语言耦合 | 需要相同编程语言 | 语言无关（JSON/Protobuf） |
| 增长趋势 | 无限制 | 受事件数量限制，而非代码量 |

> **注册表中放什么**
>
> 只放集成事件（跨组件边界的事件）。组件内部的领域事件（如仅在 Order 组件内使用的 `OrderLineItemAdded`）**不出现**在注册表中。它们是组件的私有实现。

### 3.5 张力 5：跨组件数据一致性

**问题：** 当组件需要彼此的数据时，Explicit Architecture 提供了两种模式（共享存储 + 只读查询，或隔离存储 + 事件同步的本地副本），但没有讨论任何一种选择的一致性、故障和演进影响。

**澄明决策：根据部署拓扑选择模式，并始终为最终一致性配备补偿机制。**

**模式 A：模块化单体（单一可部署单元）**

- **使用数据库视图（View）作为读契约。** 组件 A 拥有自己的表，并暴露一个 SQL View（或物化视图）供其他组件读取。组件 B 查询 View，而非原始表。
- **View schema 是一份契约。** A 可以自由修改内部表结构，只要 View 的契约不变。这将内部存储演进与跨组件读取解耦。
- **不需要事件同步的本地副本。** 在单数据库的单体应用中，强一致性是免费的。在此处使用最终一致性只是增加复杂度而无收益。

**模式 B：微服务（多个可部署单元）**

- **本地副本是必须的。** 每个服务维护一份它需要的来自其他服务的数据的只读投影，通过集成事件更新。
- **三个必需的配套机制：**
  1. **幂等事件处理器：** 对同一事件的多次处理产生相同结果。通过已处理事件 ID 表或自然幂等性来实现。
  2. **补偿事务：** 每个依赖最终一致数据做出的状态变更都必须有定义好的回滚或纠正路径。
  3. **定期对账：** 一个调度作业（如每晚运行）将本地副本与真实来源进行比对，并为任何偏差发出纠正事件。

> **选错模式的代价很高**
>
> 在单体内使用模式 B（事件同步的本地副本）是过度工程化。跨微服务使用模式 A（共享数据库视图）创造了隐性耦合。将模式与部署拓扑匹配。

---

## 第四部分：组件设计

### 4.1 什么是组件？

组件是围绕业务子域组织的粗粒度代码单元。示例：Order、Billing、Inventory、UserManagement。组件是代码组织的主轴（按组件打包），横切各层。

每个组件包含自己的：

- Domain Model 区域（实体、值对象、领域事件、仓储接口）。
- Domain Services 区域（纯跨实体逻辑）。
- Application 区域（handler、命令、查询、端口、DTO）。
- Infrastructure 区域（仓储实现、适配器实现）。

组件之间不共享领域模型类型。如果两个组件都需要"货币金额"这一概念，各自定义自己的值对象。共享的低层级工具（如 Money 类型）可以存在于一个小型共享库中，但这是代码共享决策，而非架构决策。

### 4.2 组件间通信规则

| 通信类型 | 机制 | 耦合程度 |
|----------|------|----------|
| 触发另一组件的逻辑 | 通过 Event Bus 的集成事件 | 低（异步、仅 schema 契约） |
| 触发需要即时响应的逻辑 | 通过服务发现或内部 API 的同步调用 | 中（需要可用性） |
| 读取另一组件的数据（单体） | 源组件拥有的数据库 View | 低（仅 schema 契约） |
| 读取另一组件的数据（微服务） | 通过集成事件同步的本地副本 | 低（最终一致性） |

**规则：** 一个组件永远不能直接导入另一个组件的 Domain Model、Domain Services 或 Application 区域中的类。唯一的共享产物是 Event Registry schema。

### 4.3 组件内部结构（推荐目录布局）

以下布局适用于每个组件，以典型的 Java/Kotlin/TypeScript 项目为参考：

| 目录 | 区域 | 包含 |
|------|------|------|
| `component/domain/model/` | Domain Model | 实体、值对象、枚举、领域事件 |
| `component/domain/ports/` | Domain Model | 仓储接口（写侧） |
| `component/domain/services/` | Domain Services | 纯跨实体逻辑类 |
| `component/application/commands/` | Application | Command 类及其 Handler |
| `component/application/queries/` | Application | Query 类及其 Handler + DTO |
| `component/application/ports/` | Application | 被驱动端口接口（邮件、搜索等） |
| `component/application/events/` | Application | 应用事件派发 |
| `component/infrastructure/persistence/` | Infrastructure | 仓储实现、ORM 映射 |
| `component/infrastructure/adapters/` | Infrastructure | 外部 API 客户端、邮件发送器等 |
| `component/ui/rest/` | UI / Infrastructure | REST 控制器（驱动适配器） |
| `component/ui/cli/` | UI / Infrastructure | CLI 命令（驱动适配器） |

---

## 第五部分：控制流

### 5.1 命令流（写路径）

一个典型的写操作遵循以下序列：

1. **Controller** 接收 HTTP 请求，验证输入格式，构造一个 Command DTO。
2. **Command Bus** 将 Command 路由到其注册的 Handler。
3. **Handler** 通过 Repository 端口（在构造时注入的被驱动适配器）加载所需实体。
4. **Handler** 调用 Domain Service 或 Entity 方法执行业务逻辑（纯计算，无 I/O）。
5. **Handler** 通过 Repository 端口持久化修改后的实体。
6. **Handler** 向 Event Bus 派发应用事件（如 `OrderPlaced`）。
7. 其他组件（或同一组件）中的**事件监听器**异步响应该事件。

### 5.2 查询流（读路径）

一个典型的读操作遵循以下序列：

1. **Controller** 接收 HTTP 请求，构造一个 Query DTO。
2. **Query Handler** 调用 ReadAuthorization 验证访问权限。
3. **Query Handler** 执行优化的数据库查询（可能使用裸 SQL、View 或读优化的投影）。
4. **Query Handler** 返回一个扁平 DTO（无领域实体）。
5. **Controller** 如有需要将 DTO 包装进 ViewModel，返回响应。

注意：读路径故意不加载实体或调用领域逻辑。它的唯一目的是高效地返回数据。业务规则在写路径上强制执行；读路径信任已持久化的数据是有效的。

### 5.3 跨组件事件流

1. **组件 A 的 Handler** 派发一个集成事件（如 `OrderPlaced`）。
2. **Event Bus** 将事件投递给所有注册的监听器。
3. **组件 B 的事件监听器** 将事件负载反序列化为自己的 DTO（反腐败层）。
4. **组件 B 的监听器** 构造一个内部 Command 并派发到 B 自己的 Bus。
5. **组件 B 的 Handler** 使用其自身的领域逻辑处理该 Command。

> **事件监听器纪律**
>
> 事件监听器应该只做一件事：将外部事件翻译为内部命令。它绝不应该包含业务逻辑。如果监听器超过 10 行代码，说明逻辑正在从 Handler 中泄漏出来。

### 5.4 事务边界规则

事务是基础设施关注点。以下规则规定 `@Transactional` 的归属，以及外部 I/O 如何与事务边界交互。

#### 默认：事务在 Persistence Adapter 上

对于常见情况——一个 Handler、一个聚合根——将 `@Transactional` 放在 Persistence Adapter 的 `save()` 方法上。事务范围尽可能小，Handler 中的所有外部 I/O 自然落在事务之外。

```
Handler.handle():
  ① externalClient.fetchData()     ← 尚无事务
  ② aggregate = Aggregate.create() ← 内存操作
  ③ repository.save(aggregate)     ← @Transactional 在这里开启并提交
  ④ emailSender.send(...)          ← 事务已提交
```

#### 外部 I/O 与事务边界

两类外部调用需要不同处理方式：

**前置调用（只读，在业务逻辑之前）：** 在任何持久化操作之前调用。因为 `@Transactional` 在 Persistence Adapter 上，网络调用期间不持有 DB 连接，无需特殊处理。

**提交后副作用（邮件、推送通知、Webhook）：** 必须在 DB 事务提交**之后**执行。有两种机制可选，根据副作用的性质决定：

**在 Handler 中 `save()` 之后直接调用：** 当 `@Transactional` 在 Persistence Adapter 上时，`save()` 在返回前已完成提交，Handler 中其后的任何调用都自然在提交后执行。适用场景：
- 副作用是该命令的**主要目的**（例如 `SendNotificationCommandHandler`——发送邮件本身就是目的）。
- 调用方需要知道副作用是否成功（需要同步响应）。
- 只有一个副作用，没有解耦的必要。

**Domain Event + `@TransactionalEventListener(phase = AFTER_COMMIT)`：** 聚合根在状态变化时注册 Domain Event，监听器仅在事务提交后触发。适用场景：
- 副作用是对状态变化的**被动反应**，而非命令的主要目的。
- 同一状态变化需要触发**多个**独立响应。
- 副作用需要与 Handler **解耦**，以便新增响应行为时无需修改 Handler。
- 副作用跨越**服务边界**（此时 Domain Event 通过 Outbox 成为集成事件——见 §5.3）。

**基础设施副作用（缓存失效、搜索索引更新）：** 不携带业务语义，不属于 Handler 也不属于 Domain Event 监听器。应放在 Persistence Adapter 的 `save()` 内部，Handler 无需感知。

| 副作用类型 | 机制 |
|-----------|------|
| 命令的主要目的 | Handler 中 `save()` 之后直接调用 |
| 状态变化的被动反应，同服务，单一消费者 | 直接调用或 Domain Event（优先选简单的） |
| 状态变化的被动反应，同服务，多个消费者 | Domain Event + `@TransactionalEventListener` |
| 跨服务反应 | Domain Event → 通过 Outbox 的集成事件（必须） |
| 基础设施操作（缓存、索引） | Persistence Adapter `save()` 内部处理 |

> **规则：永远不要在事务边界内执行外部 I/O（HTTP 调用、邮件发送、消息发布）。** 在网络调用期间持有 DB 连接会在高负载下耗尽连接池。它也让事务边界在语义上变得错误：回滚无法撤销已经发出的邮件。

#### 跨聚合根的原子性（同一数据库）

当一个用例必须在同一数据库中原子地更新两个聚合根时，将 `@Transactional` 放在 CommandHandler 上以覆盖两次 `save()` 调用：

```
@Transactional   ← 在 Handler 上
Handler.handle():
  ① 加载 AggregateA、AggregateB
  ② domainService.coordinate(a, b)  ← 纯计算
  ③ repoA.save(a)
  ④ repoB.save(b)                   ← 与 ③ 在同一事务中
```

这是一个显式的架构决策，需要记录在 ADR 中。不要默认这样做——首先质疑聚合根边界是否划正确。总是一起变化的两个对象往往应该是同一个聚合根。

> **`@Transactional` 绝不能出现在 Domain Service 上。** Domain Services 是无基础设施依赖的纯函数。`@Transactional` 是有运行时行为的注解，会生成 Spring 代理并引入对 `PlatformTransactionManager` 的隐性依赖。标注了 `@Transactional` 的 Domain Service 无法脱离 Spring 上下文测试，其事务行为对调用方也是不可见的。事务范围是编排决策，属于 Handler 的职责。

#### 跨服务的原子性（不同数据库）

跨服务边界不可能有 DB 级别的原子性。使用 **Saga 模式**：每一步都是对自己聚合根的独立原子操作，失败时通过领域事件触发的补偿事务来回滚。

```
PlaceOrderHandler:
  save Order(PENDING) + outbox(OrderPlaced)  ← 原子提交

Catalog 消费 OrderPlaced:
  预留库存 → 发出 StockReserved 或 StockReservationFailed

Order 消费 StockReserved:
  确认订单  ← 原子提交

Order 消费 StockReservationFailed:
  取消订单  ← 补偿事务
```

Outbox Pattern 已经为每一步提供了原子性原语：状态变更与事件发布在同一事务中写入，保证至少一次投递。

---

## 第六部分：按项目规模的分级指南

不是每个项目都需要完整仪式。澄明架构设计为可以上下伸缩。下表描述了每个阶段应采纳什么：

| 方面 | 小型（1–3 人，< 1 年） | 中型（5–15 人，长期维护） | 大型（多团队，平台） |
|------|------------------------|--------------------------|---------------------|
| 用例容器 | Application Service（直接调用） | Handler（通过 Command Bus） | Handler（通过 Command Bus） |
| Domain Services | 内联在 Application Service 中 | 独立的纯函数类 | 独立的纯函数类 |
| Repository 接口 | 在 Domain Model 区域 | 在 Domain Model 区域 | 在 Domain Model 区域 |
| CQRS 分离 | 可选（单模型可行） | 是，分离 Command/Query | 是，可能分离数据库 |
| Command/Query Bus | 否（直接注入） | 是（同步派发） | 是（支持异步） |
| Event Bus | 否（直接方法调用） | 是（进程内） | 是（分布式，如 Kafka） |
| Event Registry | 不需要 | 仓库中的 JSON schema | Protobuf/Avro + CI 检查 |
| 跨组件数据 | 直接导入 OK | 数据库 View | 本地副本 + 对账 |
| Shared Kernel | 允许（小型） | 仅 Event Registry | 仅 Event Registry |
| 静态分析 | 可选 | 推荐 | 必须（CI 强制） |

> **迁移路径**
>
> 从"小型"列开始。当团队或代码库超出其能力时，向右移动一列。铁律（Domain Model 不依赖任何东西）在每个阶段都成立。其他所有决策都是关于在 Domain Model 周围添加多少间接层。

---

## 第七部分：执行与测试策略

### 7.1 架构适应度函数

不被工具强制执行的规则终将被违反。澄明架构规定以下适应度函数，作为 CI 流水线的一部分执行：

| 规则 | 工具示例 | 检查内容 |
|------|----------|----------|
| Domain Model 零外部导入 | ArchUnit / Deptrac / dep-cruiser | 不导入 application、infra 或框架包 |
| Domain Services 无注入的端口 | ArchUnit / Deptrac / dep-cruiser | 构造函数参数仅为 Domain Model 类型 |
| Handler 不包含业务规则 | 代码评审清单（手动） | Handler 仅编排；业务条件上的 if/else → 提取到 Domain |
| 无跨组件域导入 | ArchUnit / Deptrac / dep-cruiser | 组件 A 的 domain 包不被组件 B 导入 |
| 事件 schema 向后兼容 | Schema Registry CI 插件 | 无字段删除或类型变更（除非版本升级） |

### 7.2 按区域的测试策略

| 区域 | 测试类型 | 依赖 | 速度目标 |
|------|----------|------|----------|
| Domain Model | 单元测试 | 无（不需要 mock） | < 1ms / 测试 |
| Domain Services | 单元测试 | 无（传入内存实体） | < 1ms / 测试 |
| Handler | 集成测试 | 内存仓储 / 端口的测试替身 | < 100ms / 测试 |
| Infrastructure 适配器 | 集成测试 | 真实 DB（testcontainers）或沙盒 API | < 1s / 测试 |
| 端到端（按组件） | 验收测试 | 全栈，隔离的组件 | < 5s / 测试 |
| 跨组件 | 契约测试 | Pact 或 schema 级验证 | < 500ms / 测试 |

**关键洞察：** Domain Model 和 Domain Services 区域应该拥有最多的测试数量和最快的执行速度。如果你的测试金字塔是倒置的（集成测试多于单元测试），说明纯逻辑和 I/O 之间的边界可能画在了错误的位置。

---

## 第八部分：应避免的反模式

| 反模式 | 症状 | 澄明修正 |
|--------|------|----------|
| Domain Service 中的 Repository | Domain Service 构造函数接受 Repository 接口 | 将获取逻辑移到 Handler；以参数传递实体 |
| 双重用例容器 | 部分用例在 Service 中，部分在 Handler 中 | 项目内选择一个；通过静态分析强制执行 |
| 肥大的 Shared Kernel | 共享库有 50+ 个类；每次变更触发全量重建 | 缩减为 Event Registry（仅 schema） |
| 持久化抽象过度分层 | Repository Interface + Persistence Interface + ORM Adapter = 简单 save() 要过 3 层 | 一个 Repository Interface + 一个实现就够了 |
| 贫血领域模型 + 肥大 Handler | 实体是数据袋；所有逻辑都在 Handler 中 | 将业务规则推入 Entity 方法；Handler 仅编排 |
| 读路径穿过 Domain Model | Query Handler 加载实体只为提取字段 | 使用直接 SQL/View 查询返回 DTO；跳过实体加载 |
| 含业务逻辑的事件监听器 | 监听器做复杂处理，而非仅做翻译 | 监听器构造一个 Command；派发到自己的 Handler 处理 |
| 投机性抽象 | 接口只有一个实现，也没有替代方案的计划 | 移除接口；在需要第二个实现时再添加 |
| Domain 层的有运行时行为注解 | Domain Model 类或 Domain Services 上出现 `@Entity`、`@Transactional` 或 JPA 关联注解 | 在 Infrastructure 区域创建独立的 JPA 实体类；在持久化适配器中显式完成领域对象与 JPA 实体的双向映射 |

---

## 第九部分：架构决策记录

每个采纳澄明架构的项目都应在 ADR（Architecture Decision Records）中记录其选择。以下模板涵盖了关键信息：

| 编号 | 内容 |
|------|------|
| **ADR-001：用例容器选择** | 我们使用 Command Handler 作为唯一的用例容器，因为我们已采纳 Command Bus 以获得异步能力。 |
| **ADR-002：Domain Service 纯净性** | Domain Services 是无 I/O 的纯函数。所有数据加载发生在 Handler 中（预取全部模式）。 |
| **ADR-003：Repository 接口归属** | 写侧 Repository 接口驻留在 Domain Model 区域。读侧 Query 对象驻留在 Application 区域。 |
| **ADR-004：组件间通信** | 组件通过 Event Registry 中描述的集成事件进行通信。禁止直接跨组件类导入。 |
| **ADR-005：数据一致性策略** | 我们使用数据库 View 进行跨组件读取（单体拓扑）。仅在迁移到微服务时才采纳最终一致性。 |

ADR 是活文档。当一个决策被重新审视时，原始 ADR 被标记为被新 ADR 取代，以保留决策历史。

---

## 第十部分：总结

澄明架构不是一个新发明。它是一组有主见的默认值，应用于 Explicit Architecture 中模糊的接缝处。核心论点是：

> **核心论点**
>
> 以最大严格度保护 Domain Model。以务实的、适合上下文的默认值简化其他一切。当存在疑虑时，选择让 Domain Model 更容易被隔离测试的那个选项。

**五条澄清，每条一句话：**

1. **用例只有一个家：** 有 Bus 用 Handler，没有 Bus 用 Application Service。永远不要同时有两个。
2. **纯净的 Domain Services：** 所有输入作为参数，无 I/O，无副作用。
3. **Repository 在 Domain，Query 在 Application：** 写侧接口是领域语言；读侧对象是应用关注点。
4. **Event Registry 取代 Shared Kernel：** 仅 schema，无可执行代码，向后兼容演进。
5. **将一致性与拓扑匹配：** 单体用 View，微服务用本地副本加对账。

**架构的复杂度永远不应超过它所解决的问题的复杂度。** 从简单开始，按需添加接缝，让铁律指引每个决策。
