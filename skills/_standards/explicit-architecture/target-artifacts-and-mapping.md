# 产物语法与 Mapper 标准

目标架构使用封闭的默认产物语法。允许类型只有被需求触发时才创建；未列入语法的概念默认需要架构例外。

本文件定义逻辑角色，不绑定语言语法、框架后缀或产品目录。精确类名、文件名和实现形态必须结合项目 canonical profile 与 `technology-selection.md` 决定；一旦决定，同类边界保持一致。

## 目录

- [Target Conformance](#target-conformance)
- [Domain 产物](#domain-产物)
- [Application 产物](#application-产物)
- [Interfaces 产物](#interfaces-产物)
- [Infrastructure 产物](#infrastructure-产物)
- [易膨胀概念](#易膨胀概念)
- [Mapper 边界](#mapper-边界)
- [Mapper 行为](#mapper-行为)
- [Mapper 实现策略](#mapper-实现策略)
- [准入门禁](#准入门禁)

## Target Conformance

对每个目标代码级产物标记：

- `TARGET_STANDARD`：属于允许的目标类型；
- `APPROVED_EXCEPTION`：有独立职责、ADR 和明确批准；
- `TRANSITION_ONLY`：仅为迁移存在，并有删除条件；
- `REJECT`：同义包装、局部抽取或推测性扩展，应删除。

## Domain 产物

| 类型 | 默认位置 / 命名 | 创建条件 |
|---|---|---|
| Aggregate Root | `domain/model/` | 业务行为和强一致性边界 |
| Entity | Aggregate 内 | 稳定身份和独立生命周期 |
| Value Object | `domain/model/` 或项目约定位置 | 使用当前语言支持的不可变值表示 |
| Business State | Aggregate 内闭合状态表示 | 本地权利义务或合法行为变化 |
| Domain Event | `domain/event/{Aggregate}{PastTense}` | 已发生且有业务反应/审计意义 |
| Domain Service | `domain/service/{BusinessOperation}Service` | 跨对象纯业务计算 |
| Domain Specification | 项目约定位置 | 具名可组合谓词被真实复用 |
| Write Persistence Port | `domain/ports/{Aggregate}Persistence` | Aggregate 集合需要加载/保存 |

默认不创建独立 Factory、Policy、Strategy、Validator、Manager、Coordinator 或 Entity Repository。创建行为优先放 Aggregate 工厂方法，业务规则优先放 Aggregate/VO。

## Application 产物

| 类型 | 默认位置 / 命名 | 规则 |
|---|---|---|
| Command | `application/command/{aggregate}/{Action}{Aggregate}Command` | 改变业务状态的意图 |
| CommandHandler | 同包 `{Action}{Aggregate}CommandHandler` | 唯一单用例编排者 |
| Command Result | 同包 `{Action}{Aggregate}Result` | 从本次用例内存状态产生 |
| Query | `application/query/{aggregate}/{Criteria}{Aggregate}Query` | 读取意图 |
| QueryHandler | 同包 `{Criteria}{Aggregate}QueryHandler` | 通过 Read/Search Port 返回投影 |
| Query Result | 同包 `{Aggregate}{Purpose}Result` | 独立读模型 |
| Command Mapper | 同包 `{Aggregate}CommandMapper` | Command ↔ Domain input/Result |
| Outbound Port | `application/port/outbound/` | Client、Read/Search、Cache 等外部能力 |
| Process Manager / Saga | ADR 确定 | 跨时间等待、持久状态和补偿 |

Command Handler 不返回 Query Result 或外部协议 Response。单用例不增加 ApplicationService、Coordinator 或 Orchestrator 包装。

## Interfaces 产物

| 类型 | 默认位置 / 命名 | 职责 |
|---|---|---|
| Command Controller | `interfaces/rest/{Aggregate}CommandController` | Request → Command，Result → Response |
| Query Controller | `interfaces/rest/{Aggregate}QueryController` | params → Query，Result → Response |
| Protocol Input/Output | `interfaces/{protocol}/` | 外部协议合同 |
| Boundary Mapper | `{Boundary}{Aggregate}Mapper` 或项目统一命名 | Protocol ↔ Application |
| Message Consumer | `interfaces/messaging/` | 监听并交给统一处理能力 |
| Message Handler | consumer 同边界 | 外部消息 → Command → Bus |
| Message Mapper | `{SourceOrContract}MessageMapper` | schema → 本地 input |
| Business Reaction Adapter | `interfaces/event/` 或项目约定位置 | Domain Event → Command → Bus |

Controller、Consumer 和 Listener 不包含业务判断，不直接注入具体 Handler，不创建业务 Router。

## Infrastructure 产物

| 类型 | 默认位置 / 命名 | 规则 |
|---|---|---|
| Persistence Record/Entity | `infrastructure/persistence/` 或项目约定位置 | 独立于 Domain，可含技术派生字段 |
| Data-access Repository | persistence adapter 内部 | 只服务 Adapter |
| Persistence Adapter | `{Aggregate}PersistenceAdapter` | 实现 Domain Persistence |
| Persistence Mapper | `{Aggregate}PersistenceMapper` 或项目统一命名 | Domain ↔ persistence representation |
| Read Model Mapper | `{Aggregate}ReadModelMapper` | projection → Query Result |
| Search Adapter / Mapper | `infrastructure/search/` | search representation ↔ Query Result |
| Client Adapter / Mapper | `infrastructure/client/` | Port ↔ provider DTO |
| Cache Adapter / Mapper | `infrastructure/cache/` | cache representation ↔ Result |
| Integration/Durable-intent Mapper | messaging/delivery concern | Domain Event → integration/durable representation |
| Technical Reaction | `infrastructure/{concern}/` | cache/index 等技术反应 |
| Migration / Constraint | 项目约定位置 | 持久化保障和演进 |

Durable Intent、Retry、Idempotency、Bus、异常处理等机制优先扩展项目的公共能力模块。服务只保留稳定扩展点所需的业务适配。

## 易膨胀概念

| 名称 | 默认裁决 | 自然所有者 / 例外 |
|---|---|---|
| `Assembler` | 拒绝 | 层间转换使用专属 Mapper |
| `Router` | 拒绝 | CommandBus、QueryBus、既有 Consumer |
| `ApplicationService` / `UseCaseService` | 拒绝 | Command/Query Handler |
| `Coordinator` / `Orchestrator` | 拒绝 | 普通流程由 Handler；持久长流程才是 Saga |
| `Manager` | 拒绝 | 名称不表达稳定职责 |
| `Facade` | 拒绝 | Bus 是 driving side 入口 |
| `Validator` | 拒绝 | 协议规则在 Interfaces，业务规则在 Domain |
| `Helper` / `Utils` / `Common` | 拒绝 | 行为回到自然所有者 |
| 服务内 `Processor` / `Dispatcher` / `Registry` | 默认拒绝 | 优先复用/升级公共机制 |
| `Factory` / `Builder` | 默认拒绝 | Aggregate 工厂方法或技术 SDK builder |
| `Policy` / `Strategy` | 默认拒绝 | 真实可替换算法才例外 |
| 泛化 `Mapper` / `BaseMapper` | 拒绝 | 使用边界限定 Mapper |
| `Converter` / `Translator` | 层间转换时拒绝 | 框架要求的 Adapter 内技术转换除外 |

名称变体不改变裁决。只有独立业务语义、生命周期、外部边界或机制，才可能获得例外。

## Mapper 边界

存在两套边界表示时统一使用 Mapper，字段少或映射简单也不例外。

| 边界 | 标准 Mapper | 所有者 |
|---|---|---|
| Request/Response protocol ↔ Application | `{Boundary}{Aggregate}Mapper` | Interfaces |
| External Message ↔ Application | `{SourceOrContract}MessageMapper` | Interfaces |
| Same-service Event ↔ Application | `{Aggregate}EventMapper` 或项目统一命名 | Interfaces |
| Command ↔ Domain | `{Aggregate}CommandMapper` | Application |
| Persistence Representation ↔ Domain | `{Aggregate}PersistenceMapper` 或项目统一命名 | Infrastructure |
| Projection → Query Result | `{Aggregate}ReadModelMapper` | Infrastructure |
| Search → Query Result | `{Aggregate}SearchMapper` | Infrastructure |
| External Client ↔ Port | `{Target}ClientMapper` | Infrastructure |
| Cache ↔ Result | `{Aggregate}CacheMapper` | Infrastructure |
| Domain Event → Integration/Durable Intent | `{Boundary}IntegrationMapper` 或项目统一命名 | Infrastructure |

Mapper 由依赖方向的外侧拥有。Domain 不定义或依赖 Mapper。

以下不是层间表示转换，不创建 Mapper：

- Handler 调用 Aggregate；
- Aggregate 创建 Domain Event；
- Port 调用与 Adapter I/O；
- 同一类型原样传递；
- primitive 普通计算；
- 业务校验、状态推进、补偿和路由。

## Mapper 行为

Mapper 必须：

- 无 I/O、Repository 或 Port 调用；
- 无业务决策、授权、状态推进或重试；
- 确定性转换已提供数据；
- 显式处理 null、optional、enum、时间、金额、精度和未知外部值；
- 记录有损映射、默认值和错误语义；
- 无状态；
- 使用 `toCommand`、`toDomain`、`toResult`、`toResponse` 等方向名，不用模糊 `map()`。

Infrastructure Mapper 可以：

- 计算条件约束键等纯技术派生字段；
- 展开/合并 Value Object；
- 重建当前语言和版本选定的闭合状态表示；
- 在 Domain → persistence 映射时按选定可靠机制转移待持久化事件/意图；
- 忽略不写回 Domain 的技术字段。

Mapper 不保存、不查询、不清除已保存事件，也不根据数据库冲突改变业务结果。确认保存成功后清除事件属于 Adapter。

同类转换不得分散在 Mapper、DTO `from()/of()`、构造器、Controller、Handler、Adapter 私有方法、Assembler、Converter 或 Translator 中。

## Mapper 实现策略

Target 必须统一选择：

- `EXPLICIT_MANUAL`：复杂重建、技术派生、有损规则或 ACL；
- `GENERATED_WITH_EXPLICIT_CONTRACT`：大量结构映射且编译期治理成熟；
- `STANDARDIZED_HYBRID`：按边界类别固定策略并有 ADR。

禁止反射型自动映射和静默按同名字段复制。代码生成也必须保留边界限定 Mapper，并显式实现有损、状态翻译和技术派生规则。

## 准入门禁

对每个新产物依次判断：

1. 能否关联需求、业务规则、非功能约束或内层移交？
2. 现有 Aggregate、VO、Handler、Controller、Consumer、Adapter 或 Mapper为何不能承载？
3. 是否拥有独立语义、生命周期、状态、外部边界或机制？
4. 是否属于标准产物？
5. 若理由是复用，是否有真实消费者或变化轴？
6. 删除并合回自然所有者会丢失什么？
7. 能否保持依赖向内？

未通过门禁的概念不得进入 Target 图、表或文件清单。
