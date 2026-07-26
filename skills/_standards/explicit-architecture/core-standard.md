# 显式架构核心标准

本目录是 Explicit Architecture Skill 套件的规范模块，不是公共文件收纳区。它只保存至少被两个 Skill 消费、独立于单个 Skill 工作流和输出模板的规范性规则。生成 Target、审查 Spec、审查代码和消费 As-Is 时均以这些规则为准，不在各 Skill 内复制或改写同一标准。

以下内容不得进入本目录：

- 单个 Skill 的扫描、Git、写入、审批或报告步骤；
- 只由一个 Skill 使用的 checklist、rubric 或 playbook；
- 输出模板和可复制资产；
- 某次 feature、仓库或供应商的临时事实；
- 仅因文件路径复用而被称为“共享”的实现细节。

单个 Skill 的执行方法放入其 `references/`，输出模板放入其 `assets/`。只有 Skill 间稳定交接义务才保留为共享 contract。

案例只用于解释设计力和机制，不构成穷举。遇到未覆盖场景时必须执行 `decision-framework.md`，不得因为没有相似案例而忽略设计问题。

## 目录

- [权威与 Current / Target](#权威与-current--target)
- [规范结构](#规范结构)
- [全局发现与逐层设计](#全局发现与逐层设计)
- [依赖方向与层职责](#依赖方向与层职责)
- [语义、决策与保障](#语义决策与保障)
- [最小充分领域模型](#最小充分领域模型)
- [结构性方案](#结构性方案)
- [外部流程与状态](#外部流程与状态)
- [公共能力与领域自治](#公共能力与领域自治)
- [核心门禁](#核心门禁)

## 规范结构

- `core-standard.md`：不随技术变化的层职责和设计原则；
- `architecture-context-contract.md`：中央 Architecture、Standards、Patterns 的只读消费与治理边界；
- `as-is-consumption-contract.md`：业务服务 Baseline、Delta 和单一写入职责；
- `capability-as-is-consumption-contract.md`：公共能力 Current Ref Baseline、兼容和单一写入职责；
- `baseline-ref-profile.md`：把默认 `test/develop/feature` 角色映射到仓库实际 ref；
- `discovery-gates.md`：已有共享能力与外部合同/流程的 Target 输入门禁；
- `decision-framework.md`：从新问题推导设计的强制过程；
- `rule-index.md`：稳定 `CTX/ASIS/CASIS/DISC/DR/MECH/GEN/TECH` ID，禁止编造编号；
- `technology-selection.md`：消费 As-Is Technology Profile 并选择实现；
- `mechanism-selection.md`：按保障范围和失败模型选择机制；
- `capability-generalization.md`：公共能力和 Shared Kernel 裁决；
- `target-artifacts-and-mapping.md`：Target 架构允许的产物 Profile；
- `target-evolution-and-verification.md`：Target 演进、表达和证据。

规则中的 `MUST / SHOULD / MAY` 和 `CTX-* / ASIS-* / CASIS-* / DISC-* / DR-* / MECH-* / GEN-* / TECH-*` ID 用于 Skill 套件的统一追踪。项目 canonical 规范可增加更严格要求，不得静默放宽共享 `MUST`。

## 权威与 Current / Target

按以下顺序解析设计权威：

1. 用户对本次工作明确给出的 Target 决定；
2. 仓库声明为 canonical 的架构规范；
3. 已接受的 ADR、上游 spec 和外部合同；
4. 团队规范与模块说明；
5. 当前代码实现。

更高权威可以明确改变该顺序。例如仓库规则声明 canonical 文档高于用户临时偏好时，遵守仓库规则。

区分：

- **Target**：希望长期维持的架构、职责和机制；
- **Current Evidence**：代码、schema、配置、运行行为和测试当前是什么；
- **Transition**：从 Current 安全到达 Target 的临时路径。

当前代码不是 Target 的自动来源。范例仓库可能不完整或存在反例；也不要忽视 Current 的 API、数据、消息消费者和部署事实。

## 全局发现与逐层设计

先从端到端业务生态发现设计依据，再按照依赖方向从内向外表达：

```text
Verified Service As-Is baseline
  → Pending development / feature deltas
  → Central Architecture Context
  → Requirement and authority discovery
  → Capability Owner As-Is + service adoption evidence
  → External Contract / Process Dossier (when applicable)
  → Context synthesis
  → Domain
  → Application
  → Interfaces / Infrastructure
  → Integration Contracts
  → Verification
  → Current-to-Target Evolution
```

“每层只考虑自己”表示：

- 本层只拥有自己的语义、决策、机制和产物；
- 本层必须接收需求或内层移交的约束；
- 本层可以派生履行约束所需的结构；
- 外层细节不得反向污染内层；
- 全局责任分配仍需通盘考虑第三方、事务、并发、可靠性和公共能力。

共享能力与外部集成不是外层设计完成后的补充项。先执行 `discovery-gates.md`：

- 从 Capability As-Is 读取公共模块、契约、guarantee、版本和兼容，再从服务 As-Is 确认实际采用版本和配置；
- 对新能力先裁决 reuse/configure/compose/extend/replace/new；
- 涉及外部系统时先取得有效合同和真实流程；
- 证据不足但会改变模型或合同的部分保持 `BLOCKED`。

Service As-Is、Candidate 和 Pending Delta 的归属遵守 `as-is-consumption-contract.md`；Capability As-Is 的稳定基线遵守 `capability-as-is-consumption-contract.md`。分支角色先按 `baseline-ref-profile.md` 解析，默认仍为 `test/develop/feature`。Feature Spec 只能引用和摘录 Current，不得把未验证的 Target 或分支变化提前提升为当前事实。

中央 Architecture Context 遵守 `architecture-context-contract.md`。Feature 工作流只读中央仓库：Architecture 和 Standard 是适用时的 Target authority，Pattern 是待裁决候选，Landscape/Catalog 只负责发现相关 owner 和资料。中央上下文不能替代 Service As-Is、Capability As-Is、合同或运行证据。

## 依赖方向与层职责

目标依赖方向：

```text
Interfaces   ──┐
               ├──► Application ──► Domain
Infrastructure ─┘
```

### Domain

只表达：

- 统一语言；
- Aggregate、Entity、Value Object；
- 业务行为、不变量和本地业务状态；
- 无 I/O 的纯 Domain Service；
- 已发生的本地 Domain Event；
- 使用领域语言的写侧 Aggregate Persistence 语义。

不得依赖 Application、协议、持久化框架/查询语言、消息、缓存、搜索或第三方 SDK。只有 Aggregate Root 拥有写侧 Repository/Persistence；Domain Service 不注入 Port。

### Application

只表达：

- Command、Query 和各自独立的 Result；
- Handler 编排；
- 非领域型 Outbound Port；
- 数据预取、原子性意图和外部 I/O 顺序；
- 真正跨时间、跨事务的 Process Manager / Saga。

Handler 加载事实、调用 Domain、保存并从本次用例的内存状态返回 Command Result。业务条件由 Aggregate 或纯 Domain Service 决定。Query 默认直接使用投影，不重建 Aggregate。

### Interfaces

只表达 driving adapter：

- 当前系统实际使用的同步接口、消息消费、回调、Job、CLI 或其他入口；
- 协议 input/output 和语法校验；
- 协议错误；
- 边界映射和通过 CommandBus/QueryBus 的分发。

不得包含业务判断或直接依赖具体 Handler。

### Infrastructure

只表达 driven adapter 和技术机制：

- Persistence technology、schema、约束、锁和事务；
- Cache、Search、Durable Intent、Retry、Idempotency；
- Message publisher、remote client、Email/File 等 Adapter；
- 技术派生字段和映射；
- 技术副作用及其可靠执行。

### Events

- Domain Event：本地业务事实，属于生产服务 Domain。
- Integration Event：跨服务 schema contract，属于共享 schema 模块。
- Domain 不导入 Integration schema。
- Handler 不直接调用具体消息 publisher；跨边界投递通过已选择的可靠机制。

## 语义、决策与保障

对每条关键规则分别回答：

| 维度 | 问题 |
|---|---|
| 语义所有者 | 哪个 bounded context 定义业务含义？ |
| 决策位置 | 哪个对象或用例决定行为可否执行？ |
| 最终保障 | Domain、权威存储、事务、协调、持久化意图、幂等或其他机制？ |
| 有效时点 | 对象操作、存储语句、unit-of-work 同步、commit 或最终一致？ |
| 作用范围 | 单值、单 Aggregate、全库或跨服务？ |
| 权威来源 | 判断所需事实由谁拥有？ |

业务规则属于 Domain 语义，不等于必须增加 Domain 字段、Repository 预查询或内存校验。跨对象唯一、并发排他等规则可由拥有权威事实的持久化机制提供最终原子保障。

Infrastructure 可以根据内层语义派生无独立业务含义的技术状态：

- 条件唯一键和影子列；
- concurrency revision、排序和关联维护字段；
- Durable Intent、Retry、Idempotency 状态；
- Search、Cache 和 Query 冗余；
- correlation、delivery、dead-letter metadata。

这些产物不得进入 Aggregate、Command/Result 或外部业务合同。

仅用于说明“语义与技术表示分离”的抽象示意：

```text
Domain:
业务记录生效后，它声明的排他业务键不得被另一条生效记录占用。

Infrastructure:
不参与排他约束 → constraint_key = NULL
参与排他约束   → constraint_key = business_key
UNIQUE(constraint_key)
```

`constraint_key` 只属于 Persistence Model。具体数据库表示必须由 predicate、有效时点、约束能力和标准写入路径推导，不能从本示意直接照搬。

## 最小充分领域模型

新增 Aggregate、Entity、Value Object、Domain Service、Domain Event、Port 或中间类型前，要求：

1. 关联明确需求、规则或约束；
2. 说明独立语义、身份、生命周期、状态或外部边界；
3. 说明现有自然所有者为何不能用一个方法承载；
4. 通过删除测试：删除后会丢失什么业务信息或边界？

不要：

- 为每个 primitive 自动创建 Value Object；
- 为每个 Entity 创建写侧 Repository；
- 为每次字段变化创建 Domain Event；
- 为 DDD 形式完整创建没有行为的模型；
- 用 Domain Service 执行 I/O；
- 把重试、调用中、dead-letter 等技术状态建模为业务状态；
- 把跨服务完整流程塞入单 Aggregate。

Domain Service 只承载无法自然归属单个 Aggregate 的纯业务计算。Domain Event 必须是过去时的业务事实，并具有业务反应或审计意义。

## 结构性方案

优先：

- 声明式约束，而不是过程协调；
- 标准保存路径，而不是特例流程；
- 单一事实来源，而不是多层重复判断；
- 对所有入口和并发成立的机制；
- 技术细节留在 Adapter；
- 删除偶然复杂度，而不是用抽象隐藏它。

以下信号必须触发第二次结构性判断：

- 显式 `flush()`；
- 正确性依赖 delete/update/insert 顺序；
- Handler 感知 data-access/unit-of-work 或存储执行细节；
- 先查再写却没有原子保障；
- 新入口必须复制特殊判断；
- 为框架限制增加 Domain 状态；
- 外部 I/O 被本地资源事务包围；
- 保存、发消息或副作用依赖人工调用顺序。

不要机械要求多个备选方案。只有存在跨层耦合、脆弱时序或高代价决策时，重新寻找能让标准流程保持普通的机制。

## 外部流程与状态

第三方或跨服务交互必须先按 `DISC-03` 至 `DISC-05` 取得契约与流程证据，再在建模前考虑：

- 是否需要稳定的外部引用；
- 外部成功、失败、处理中和未知如何映射为本地语义；
- 哪些外部事实推进本地单据；
- 超时、回调、重复、乱序、补偿、对账和人工恢复；
- correlation、幂等和排序；
- 外部状态是否真正改变本地权利、义务或合法行为；
- 跨服务副作用如何可靠投递。

不要完整复制外部状态机。只有外部事实改变本地业务含义时，才映射为本地状态或事实。协议 correlation 和路由字段留在外层。

跨服务原子性使用可靠事件、Saga/补偿和对账，不假装本地资源事务能够覆盖远程副作用。

## 公共能力与领域自治

先按 `DISC-01` 从 Capability As-Is 读取权威能力契约，再从 Service As-Is 确认目标服务实际采用版本和配置，然后执行适配裁决。现有能力必须优先判断复用、配置、组合或升级，不能只在发现重复代码后才回头查共享库。

对可靠性、一致性、调度、幂等、协议、观测和资源管理机制执行去业务化测试：

1. 去掉订单、维修、库存等 bounded-context 名词；
2. 若问题仍完整描述一种机制，将其视为公共能力候选；
3. 若服务只需提供 Handler、Mapper、Payload、Policy 或配置，增强公共能力判断。

强信号：

- 服务内新建 scheduler、retry/durable-intent/idempotency 存储；
- 重复实现 backoff、dead-letter、恢复或 trace propagation；
- shared/seedwork 已有部分能力但缺稳定扩展点；
- 机制生命周期与当前业务功能独立。

公共能力洞察至少说明：

- 能力缺口；
- 为什么是机制而不是业务概念；
- 建议所有者；
- 公共稳定核心；
- 当前服务保留的适配点；
- 对现有消费者的影响。

当前需求可以是第一个消费者，不应默认成为机制所有者。但不要在洞察阶段无边界扩大实现范围。

同名或同结构的领域对象不能因为字段相同就共享。只有语义、不变量、变化方向、生命周期和共同治理一致时，才提出 Shared Kernel。

## 核心门禁

- 依赖始终向内；
- 每层只拥有本层关注点；
- 业务语义与技术保障已分离且完整衔接；
- Domain 没有技术字段、协议或框架行为；
- Handler 只编排；
- 外部状态已转换为本地语义；
- 标准保存路径没有被框架技巧破坏；
- 已有共享能力已作为 As-Is 纳入裁决；
- 外部系统契约和流程证据足以支撑本地嵌入；
- 公共机制机会和领域自治均已判断；
- 所有新增概念均最小充分；
- 关键规则在并发和失败路径下仍有人最终保障。
