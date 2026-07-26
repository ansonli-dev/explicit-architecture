# Spec 生成操作手册

本手册补充共享标准，定义如何把需求逐层转成可审查 spec。

## 目录

- [开始条件](#开始条件)
- [Architecture Context Discovery](#architecture-context-discovery)
- [Discovery Baselines](#discovery-baselines)
- [Artifact Budget](#artifact-budget)
- [Decision Analysis](#decision-analysis)
- [Technology Profile and Implementation](#technology-profile-and-implementation)
- [Context 产出](#context-产出)
- [Domain 产出](#domain-产出)
- [Application 产出](#application-产出)
- [Interfaces 产出](#interfaces-产出)
- [Infrastructure 产出](#infrastructure-产出)
- [Integration 产出](#integration-产出)
- [Verification 与 Evolution](#verification-与-evolution)
- [完成门禁](#完成门禁)

## 开始条件

先确认：

- 需求来源和目标使用者；
- spec 新建还是更新；
- 输出目录和文档约定；
- 是否 Brownfield；
- 是否能访问中央 Architecture Context 的索引和 immutable ref；
- 是否有适用的 canonical Architecture、Standards、ADR 和 Patterns；
- 是否有 Ref Profile 和绑定 resolved verified Baseline SHA 的 Canonical As-Is；
- development ref、feature merge-base 和相关 Pending Delta；
- 是否能取得相关 Capability As-Is，以及服务实际采用版本；
- Service As-Is 中是否已有外部合同/流程。

若用户只要求讨论，不立即创建文件，可以先输出设计草案；一旦要求“生成 spec”，创建文档而不是只在对话中概述。

## Architecture Context Discovery

服务功能开发只读中央仓库。开始时：

1. 固定中央 repository identity、commit/version、index version 和读取时间。
2. 从 requirement、service、protocol、capability、integration 和 environment 提取 `applies_when/applies_to`。
3. 读取匹配的 `ACTIVE` Architecture、Standards、Patterns、ADR、Landscape 和 Catalog 条目。
4. 用 Landscape/Catalog 发现相关 owner、Service As-Is、Capability As-Is 和合同入口。
5. 将 mandatory Standard 与 advisory Pattern 分开；Pattern 继续执行 DR/MECH/GEN/TECH 裁决。
6. 记录批准例外、冲突、过期、不可访问和适用性不明资料。
7. 缺口会改变边界、所有权或强制合同时标记 `ARCHITECTURE_CONTEXT_GAP`。

不要递归加载整个中央仓库。不要在生成 Spec 时移动、补写或修订中央文档；输出 Architecture Governance Candidate。

## Discovery Baselines

本流程消费 Canonical Service Baseline 和 Capability Baseline，不负责重建。建立 Artifact Budget 和 Domain 前：

1. 验证 Service Ref Profile、Baseline Manifest、full SHA、environment 和 `VERIFIED` status。
2. 验证相关 Capability Baseline 使用 resolved current-ref/full SHA 和明确 evidence scope。
3. 从 Capability As-Is 摘录 module/version、contract、guarantee、compatibility、extension、consumers 和限制。
4. 从 Service As-Is 摘录实际 resolved version、配置、本地扩展和采用偏差。
5. 摘录相关 External Integration Dossier 和 readiness。
6. 读取业务服务 verified Baseline → development ref 的 Pending Delta。
7. 若 feature 已有实现，读取实际 merge-base → feature ref 的 Delta。
8. 按 `DISC-02` 裁决 reuse/configure/compose/extend/wrap/replace/new/local。

Service Baseline 缺失、过期或证据不足时输出 `BASELINE_GAP`；Capability Baseline 缺失、与 resolved capability current-ref SHA 不一致或不可信时输出 `CAPABILITY_BASELINE_GAP`。不要在 Feature Spec 中复制临时 As-Is。

## Artifact Budget

在详细设计前初始化 Artifact Inventory：

1. 先列现有可 `KEEP/MODIFY` 的标准产物；
2. 再登记需求触发的 `ADD`；
3. 为每个产物标记 layer、type、location 和 source；
4. 为非标准类型执行准入门禁；
5. 未登记的代码级产物不得突然出现在外层 spec。

Artifact Budget 是控制复杂度的工具，不是事后文件清单。

## Decision Analysis

为每个会影响边界、一致性、可靠性、外部状态或公共能力的决定：

1. 按 DR-01 写出不带实现的目标。
2. 按 DR-02 分类 Design Forces。
3. 按 DR-03 确认 authority、scope 和 effective point。
4. 按 DR-04 建立 failure model。
5. 按 DR-05 分配 semantic/decision/mechanism/operational owner。
6. 从 MECH 机制族选择组合，不从技术名词直接开始。
7. 执行 standard-path、counterfactual 和 deletion tests。
8. 执行 GEN 公共能力判断。
9. 定义演进和验证证据。

只有影响边界或高代价的决定需要完整 ADR；其他决定可以在 Context Decision Matrix 中压缩记录。

## Technology Profile and Implementation

架构意图和机制族确定后：

1. 从 build、lock、runtime、deployment 和 server 配置确认 effective stack。
2. 建立只覆盖相关能力的 Technology Capability Profile。
3. 对版本敏感能力读取当前精确版本的官方 specification/reference，或使用本地 dependency/source/capability query。
4. 检查 compiler、framework、serializer、data-access/unit-of-work、driver 和 migration 的组合限制。
5. 检查 local/test/staging/production 的能力交集。
6. 按 TECH-07 选择最优实现并记录 fallback。
7. 将具体语法、产品和版本只写入拥有它的外层或 Technology Decision，不写入 Domain。

如果 stack/version 未确认，继续完成技术中立的 Target intent，将具体 implementation decision 标记为 `BLOCKED` 或 `UNKNOWN`。

## Context 产出

至少回答：

- 中央 Architecture Context 的 commit/version、选中文档、权威和适用性；
- Canonical Service/Capability As-Is 和相关 Pending Delta；
- 已有共享能力及其 fit/gap/decision；
- 外部合同、流程证据和 readiness；
- 触发到业务结果的完整流程；
- 参与者与系统边界；
- 正常、失败、未知和恢复路径；
- 事实和状态所有者；
- 外部状态到本地语义的映射；
- 关键规则的语义、决策和保障分配；
- 假设、排除范围和阻塞问题。

流程用 `flowchart`，跨系统交互用 `sequenceDiagram`。事实来源、责任分配和规则追踪用表格。

## Domain 产出

输出：

- 统一语言；
- Aggregate 边界和对象关系；
- 行为、不变量和拒绝结果；
- 状态机；
- Domain Event；
- 外部引用；
- 写侧 Persistence 能力；
- 外层保障移交。

使用精简 `classDiagram` 和 `stateDiagram-v2`。图中不出现持久化表示、Mapper、Command、具体协议或消息产品。

对每个模型元素执行删除测试。跨 Aggregate/全库规则可以只表达语义并移交数据库保障，不为“属于 Domain”强行增加字段或 Port。

## Application 产出

输出：

- Command/Query/Result 清单；
- Handler 步骤；
- Command Mapper contract；
- Outbound Port；
- 原子性和 I/O 顺序；
- Process Manager / Saga（确有需要时）；
- Domain/Application/Infrastructure/Protocol 错误边界。

复杂用例用 `sequenceDiagram` 或 `flowchart`。图中使用抽象 Port，不出现具体 Adapter、存储查询语言或 provider SDK。

## Interfaces 产出

输出：

- 当前协议的 operation/channel、input 和 output；
- Controller/Consumer/Listener；
- Boundary/Message Mapper contract；
- 协议语法校验；
- 错误码和版本；
- callback/webhook 时序。

字段和错误合同用表格；多轮协议才画时序图。

## Infrastructure 产出

输出：

- 与当前存储模型匹配的 Persistence Model 图；
- 当前存储支持的约束、冲突检测、revision 与技术派生表示；
- Persistence/Read/Search/Client/Cache/Integration Mapper；
- save/transaction/durable-intent/retry 时序；
- 外部 I/O 和技术副作用；
- migration、backfill、rollback；
- Mapping Implementation Decision。
- Technology Capability Profile 和 `TECH-*` 实现决定。

每个技术派生产物记录：

- 来源约束；
- 推导规则；
- 生命周期；
- 最终保障；
- 不得泄漏的内层。

## Integration 产出

输出：

- 外部 contract/process evidence、version、environment 和 readiness；
- accepted/processing/completed/rejected/unknown 等外部事实的权威流程；
- Domain Event 与 Integration Event 对照；
- schema、owner、version、compatibility；
- delivery、idempotency、ordering、replay；
- 外部引用和状态映射；
- timeout、unknown、duplicate、compensation、reconciliation。

时序用 `sequenceDiagram`；schema 和兼容使用表格；补偿/对账用 `flowchart` 或 Saga 状态图。

## Verification 与 Evolution

Verification 覆盖：

- Domain behavior；
- Handler orchestration；
- Mapper special cases；
- Adapter guarantees；
- contract compatibility；
- concurrency and failure recovery；
- migration and rollback。

Evolution 记录 Current、Target、Artifact Delta、兼容矩阵、数据迁移、过渡产物和阶段 gate。

不要把 rollout 步骤写成无验收标准的机械任务。每个阶段必须有进入条件、完成证据和回滚点。

## 完成门禁

- 所有需求有稳定 ID；
- 中央上下文已按索引发现并固定版本；
- mandatory Standard、advisory Pattern、Current As-Is 权威没有混淆；
- 中央上下文缺口已阻塞或形成 Governance Candidate；
- Context 足以支持模型判断；
- Service As-Is 记录 Ref Profile 并绑定 resolved verified Baseline SHA；
- Pending Develop Delta 已纳入设计；
- Capability As-Is 已绑定 resolved capability current-ref/full SHA，服务采用版本已确认，且不存在无依据的平行机制；
- 外部集成达到相应设计所需 readiness；
- Domain 最小充分且纯净；
- 外层完整履行内层约束；
- Mapper 路径唯一；
- 无未批准的新产物类型；
- 公共能力和 Shared Kernel 已分别判断；
- 外部状态和失败路径完整；
- Brownfield 有安全演进；
- 图、表和短文各自承载适合的信息；
- 追踪链闭合；
- 阻塞问题没有被假设掩盖。
