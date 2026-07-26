---
name: to-spec
description: Create a specification for a feature that has not yet been developed, using requirements, a read-only central Architecture Context, a verified service As-Is baseline, relevant shared-capability owner baselines, pending deltas, approved decisions, and external contracts. Produces an inside-out Target design, Current-to-Target delta, integration design, governance candidates, and verification requirements. Use when asked to design, draft, create, or refine a new feature spec; do not use to curate central architecture documents, create or update any As-Is, approve a developer-authored spec, review implemented code, or generate an implementation plan.
---

# 显式架构 Spec 生成

从需求建立完整设计依据，再按照 Domain → Application → Interfaces / Infrastructure / Integration 的依赖方向逐层产出 spec。全局判断责任，只在每层写入本层拥有的内容。

## 标准读取路由

开始前只完整读取 `../_standards/explicit-architecture/core-standard.md`。进入证据固定、设计或写入阶段时按 Trigger 完整读取：

| Trigger | Read |
|---|---|
| 开始生成并准备写入分层 Spec | `references/generation-playbook.md` |
| 解析 Service/Capability 实际 ref role | `../_standards/explicit-architecture/baseline-ref-profile.md` |
| 消费中央 Architecture Context 或输出治理候选 | `../_standards/explicit-architecture/architecture-context-contract.md` |
| 使用 Service Baseline、Pending Delta 或 As-Is Candidate | `../_standards/explicit-architecture/as-is-consumption-contract.md` |
| 使用公共能力 Current、版本、兼容或扩展点 | `../_standards/explicit-architecture/capability-as-is-consumption-contract.md` |
| 判断共享能力 fit/gap 或外部 contract/process readiness | `../_standards/explicit-architecture/discovery-gates.md` |
| 作出非平凡架构决定或执行反事实测试 | `../_standards/explicit-architecture/decision-framework.md` |
| 设计代码级 Target 产物、Mapper 或 Artifact Budget | `../_standards/explicit-architecture/target-artifacts-and-mapping.md` |
| Brownfield、兼容、迁移、验证或过渡产物 | `../_standards/explicit-architecture/target-evolution-and-verification.md` |
| 选择具体 stack/version 下的实现 | `../_standards/explicit-architecture/technology-selection.md` |
| 一致性、并发、事务、异步副作用、重试、外部交互、长流程或技术派生状态 | `../_standards/explicit-architecture/mechanism-selection.md` |
| 非领域机制、shared/seedwork 扩展、复用或 Shared Kernel | `../_standards/explicit-architecture/capability-generalization.md` |
| 输出标准 ID | `../_standards/explicit-architecture/rule-index.md` |

未触发的标准不要预加载。触发后必须完整读取对应文件，引用标准 ID 前必须读取 `rule-index.md`。

共享标准是 Skill 套件的唯一规范源。不要在本 spec 中重新发明一套层、产物或 Mapper 规则。

## 工作边界

本 skill：

- 从需求生成新的 Target spec；
- 消费 verified Service/Capability As-Is、Pending Delta 和批准的决定；
- 只读消费中央 Architecture Context，并固定实际使用的 commit/version 和 document IDs；
- 按需读取与 Delta/可行性有关的 Current 代码；
- 创建或更新 spec 文档；
- 不生成生产代码；
- 不机械拆分开发任务；
- 不创建、重建、验证或更新任何 Canonical As-Is；
- 不创建、移动、整理或修改中央架构仓库；
- 不把待审的 developer spec 直接判为通过。

业务服务 As-Is 缺失或过期时输出 `BASELINE_GAP`；公共能力 As-Is 缺失或过期时输出 `CAPABILITY_BASELINE_GAP`。审核已有 spec、扫描实现和维护 Current 均不在本 Skill 范围内。

## 权威与事实

开始时明确：

- 用户要求和明确 Target 决定；
- 中央仓库和服务仓库中适用的 canonical Architecture、Standards 与 ADR；
- 相关 Patterns、Landscape、Capability/Integration Catalog 及其权威等级；
- Service Ref Profile 与最后一个 resolved `VERIFIED` Baseline SHA；
- Capability Ref Profile 与 resolved current-ref/full SHA、module/version、contract、guarantee、defaults、extension 和 compatibility；
- verified Baseline → development-ref SHA 的相关 Pending Develop Delta；
- 实际 feature merge-base 和 Feature Delta（若已有实现）；
- 已接受 ADR、外部合同和上游 spec；
- 项目公共模块、共享服务、platform 能力及其真实 consumers；
- 外部系统当前有效的 contract、版本、状态/流程与运行约束；
- 当前代码、schema、配置、部署和测试；
- 语言、framework、storage、messaging 等有效版本和 capability evidence；
- 假设与未决问题。

Service As-Is 是稳定运行 Current，Capability As-Is 是 resolved capability current-ref/full SHA 的稳定公共能力 Current；业务服务的 development/feature 变化以及 capability current ref 之外的其他变化都不能提前写成 Canonical Current。默认 Profile 仍为 `test/develop/feature`。代码是 Current Evidence，不自动成为 Target。仓库本身可能是范例、缺失实现或包含反例；但实际文档写入和代码库操作仍遵守仓库规则。

## Spec 生成工作流

### 1. 固定 Architecture Context 与 Baseline Inputs

在 Domain 设计前确认：

- 中央仓库 identity、immutable commit/version、index version 和读取时间；
- 根据 requirement、service、protocol、capability 和 environment 选中的 document IDs；
- 每项中央文档的 type、status、authority、scope、applicability 和已批准 exception；
- 从 Landscape 发现的相关服务、owner、repository、本地 As-Is 和 contract 入口；
- 适用 Architecture、mandatory Standards 和 ADR constraints；
- 相关 Patterns 的 forces、guarantee、适用条件、限制和实现变体；Pattern 不自动成为决定；
- Capability/Integration Catalog 指向的自然 owner 资料；
- Service Ref Profile、As-Is Baseline ID、resolved verification ref/full SHA、environment、`VERIFIED` status 和 evidence timestamp；
- development ref、Pending Develop Delta、feature ref 和实际 merge-base；
- 已批准但尚未提升的 Spec/ADR；
- 从 Capability As-Is 摘录的相关 Shared Capability：Ref Profile、Baseline ID、resolved current-ref/full SHA、module/version、contract、guarantee、non-guarantee、compatibility、consumers、extension point 和限制；
- 从 Service As-Is 确认目标服务实际 resolved version、configuration/default override、本地 extension/Adapter 和采用偏差；
- 每个需求能力与现有能力的 `REUSE_AS_IS / CONFIGURE / COMPOSE / EXTEND_SHARED / WRAP_LOCAL_ADAPTER / REPLACE_OR_UPGRADE / NEW_SHARED_CAPABILITY / LOCAL_ONLY` 裁决；
- 需求、业务规则、验收标准和非功能约束；
- 参与者、系统边界和端到端流程；
- 正常、拒绝、取消、补偿和恢复路径；
- 业务事实、身份和状态的权威来源；
- 第三方引用、外部状态和本地推进条件；
- 超时、重复、乱序、结果未知、对账和人工恢复；
- 从 As-Is 或受治理证据取得的 External Integration Dossier：有效契约、版本、authority、状态/流程、accepted/completed/unknown、callback/query/cancel/compensation 和 readiness；
- 会改变 Aggregate、状态机、所有权、一致性或合同的假设。
- 影响实现选择的 Technology Capability Profile；版本不明时保持 Target intent 技术中立。

Canonical Service As-Is 缺失、未验证或与 resolved Baseline SHA 明显不符时，输出 `BASELINE_GAP`。Capability As-Is 缺失、与 resolved capability current-ref SHA 不符或缺少关键 guarantee/compatibility 时，输出 `CAPABILITY_BASELINE_GAP`。不要在本 Skill 内重建后冒充权威输入；缺失证据会改变模型/合同的部分标记 `BLOCKED`。

中央索引或适用 Architecture/Standard 缺失、冲突、过期或不可访问时输出 `ARCHITECTURE_CONTEXT_GAP`。若缺口会改变边界、所有权、强制合同或批准条件，相关设计标记 `BLOCKED`；否则记录明确假设，并输出 `Architecture Governance Candidate`。Feature 流程不得直接修复中央仓库。

### 2. 分配语义、决策与保障

对关键规则记录：

- 适用 Design Forces；
- 语义所有者；
- 决策位置；
- 最终保障；
- 有效时点；
- 作用范围；
- 权威来源；
- failure model 与 prevent/detect/recover/tolerate/exclude；
- 是否暴露公共能力缺口。
- 与已有 Shared Capability 的 fit/gap 和适配决定。

这不是新架构层。将结果作为后续逐层设计的责任分配依据。

完成 DR-01 至 DR-12 后先选择机制族，再执行 TECH-01 至 TECH-08 选择当前 stack 下的实现。不得从已有类名、框架 API 或案例关键词直接推断机制。

### 3. 设计 Domain

只设计：

- 统一语言；
- Aggregate、Entity、Value Object；
- 业务行为、不变量和本地状态转换；
- 纯 Domain Service；
- Domain Event；
- 写侧 Aggregate Persistence 语义；
- 真正影响本地业务的外部引用。

不要写 Command、协议类型、持久化框架、具体消息产品、Mapper、锁、派生约束字段、Durable Intent、Retry 或第三方 SDK 类型。对外层保障的规则只写业务语义和移交要求。

### 4. 设计 Application

只设计：

- Command、Query 和各自 Result；
- Handler 编排；
- Command Mapper；
- Outbound Port；
- 数据预取、原子性意图和 I/O 顺序；
- 真正跨时间的 Process Manager / Saga；
- 稳定错误语义。

Handler 只编排，Mapper 只转换。Command Result 从本次用例内存状态产生，不额外调用 Query。

### 5. 设计 Interfaces

按需设计：

- 当前协议需要的 Request/Response/Message、Driving Adapter 和 Boundary Mapper；
- Message/Webhook Consumer、Handler 和 Message Mapper；
- 同服务业务 Listener；
- 协议校验和错误合同。

Driving Adapter 只映射和分发，不包含业务判断。

### 6. 设计 Infrastructure

按需设计：

- 当前 stack 对应的 Persistence Model、Data-access、Adapter 和 Mapper；
- 存储约束、技术派生字段、revision 和事务；
- Durable Intent、Retry、Idempotency；
- Cache、Search、Remote Client 和技术 Reaction；
- migration/schema/configuration；
- 技术异常翻译。

允许派生条件约束键等不进入 Domain 的技术结构。优先声明式约束和普通 save/commit 路径，拒绝依赖 `flush()` 或调用顺序的设计。已有共享能力能满足或可扩展时，服务只保留配置、Policy、Mapper、Handler 或 Adapter，不平行重建机制。

### 7. 设计 Integration Contracts

只在 External Integration Dossier 达到足够 readiness 后按需设计：

- 外部 contract/version/owner 与本地使用目的；
- 外部业务流程如何嵌入本地流程；
- Domain Event → Integration Event 映射；
- schema owner、字段、版本和兼容；
- 投递语义、幂等键、顺序、重复和重放；
- 外部引用和状态到本地语义的映射；
- 超时、未知、补偿、对账和人工恢复。

### 8. 设计验证

让每条关键需求可追踪到：

```text
Domain meaning/decision
  → Application orchestration
  → Interface/Infrastructure/Integration guarantee
  → Verification evidence
```

覆盖 Domain、Handler、Mapper、Adapter、Contract、并发、失败恢复和迁移。

### 9. 设计 Current → Target

Brownfield 场景记录：

- As-Is Baseline ref；
- Pending Develop/Feature Delta；
- Current Evidence；
- Target Artifact；
- Target Conformance；
- `KEEP / MODIFY / ADD / MOVE / RENAME / REPLACE / DELETE`；
- compatibility、migration、rollout、rollback；
- 过渡产物删除条件；
- 公共能力升级对消费者的影响。

先确认 Target，再设计演进。迁移妥协不得进入最终 Domain。

### 10. 全局收敛

完成前检查：

- 是否存在内层污染；
- 是否记录 Ref Profile 并固定 resolved `VERIFIED` Baseline SHA，而非移动分支名；
- 是否把 development/feature ref 变化误写成 Canonical As-Is；
- 是否遗漏 Pending Develop Delta；
- 是否把技术状态建模成业务状态；
- 是否遗漏外部流程依据；
- 是否为了框架限制破坏标准流程；
- 是否存在未识别的公共机制；
- 是否固定并引用了适用中央 Architecture Context；
- 是否把 mandatory Standard、advisory Pattern 和 Current As-Is 混为同一权威；
- 是否遗漏中央索引指向的相关服务、owner、合同或已批准例外；
- 是否将中央缺口输出为 Governance Candidate 而不是顺带修改仓库；
- 是否在取得 Capability As-Is 和服务采用事实前就提出新增局部机制；
- 是否错误共享领域对象；
- 是否在取得外部 contract/process 前就确定 Adapter、状态映射或推进规则；
- 是否有规则无人保障或重复保障；
- 是否有无需求依据的新抽象；
- 是否所有跨层表示转换都有唯一专属 Mapper；
- 是否 Target 的每个产物都有明确演进动作；
- 是否过渡路径有删除条件；
- 是否需求、设计、验证和变更可追踪。
- 是否用未出现于当前故事的第二入口、并发、崩溃、重复、乱序和版本共存完成反事实测试；
- 是否每个关键机制能引用 `CTX-* / ASIS-* / CASIS-* / DISC-* / DR-* / MECH-* / GEN-*` 规则和验证证据；
- 是否具体实现能引用 `TECH-*`、有效版本、能力证据、限制和 fallback。

## 产出位置

优先遵守仓库已有 spec 目录。没有约定时创建：

```text
specs/{feature}/
├── 00-context.md
├── 01-domain.md
├── 02-application.md
├── 03-interfaces.md
├── 04-infrastructure.md
├── 05-integration-contracts.md
├── 06-verification.md
├── 07-codebase-evolution.md
└── traceability.md
```

只创建适用的可选文档。不要创建空占位层。

使用 `assets/spec-template/` 作为起点：

- 替换所有 `{{PLACEHOLDER}}`；
- 删除不适用的可选章节；
- 用真实信息替换 Mermaid 骨架；
- 不复制模板说明；
- 不覆盖已有 spec，除非用户明确要求；
- 同一事实只保留一个权威表达。

## 每层共同结构

每个分层文档按需包含：

- `Inherited Constraints`
- `Local Decisions`
- `Derived Artifacts`
- `Mapping / Handoff`
- `Capability Insights`
- `Leakage Check`
- `Open Questions`

这些是责任检查点，不要求机械使用表格。流程、状态、时序和关系使用合适的 Mermaid；属性、约束、映射和追踪使用表格；依据和风险使用短文。

## 完成状态与协作

最终给出：

- `PASS`：设计充分且没有阻塞项；
- `PASS WITH ASSUMPTIONS`：假设明确且不改变核心边界；
- `BLOCKED`：缺失信息会改变关键设计。

同时列出 `Architecture Governance Candidates`；没有候选时明确写 `None`。候选不是中央仓库修改，也不是已批准决定。

生成结果只包含 Target Spec、Baseline/Context Gap、As-Is Delta Candidate 和 Architecture Governance Candidate。后续审核、实现检查和 Current 更新由用户按自己的流程安排。
