---
name: to-spec
description: Create a specification for a feature that has not yet been developed, using requirements, a read-only central Architecture Context, a verified service As-Is baseline, relevant shared-capability owner baselines, pending deltas, approved decisions, and external contracts. Produces an inside-out Target design, Current-to-Target delta, integration design, governance candidates, and verification requirements. Use when asked to design, draft, create, or refine a new feature spec; do not use to curate central architecture documents, create or update any As-Is, approve a developer-authored spec, review implemented code, or generate an implementation plan.
---

# 生成显式架构 Spec

从需求和受治理证据生成 Target Spec。先运行设计控制回路得到并证明合理方案，再按 Domain → Application → Interfaces / Infrastructure / Integration 逐层表达。

## Hard Gates

始终满足：

1. 只生成或修改 Target Spec；不修改生产代码、Canonical Service/Capability As-Is 或中央 Architecture Context。
2. 固定实际使用的 repository、immutable commit/version、Ref Profile、environment 和 evidence timestamp；移动分支名不能代替 SHA。
3. 区分 mandatory Standard、advisory Pattern、Current As-Is、approved Target 和代码证据；低权威来源不能覆盖高权威决定。
4. Canonical Service Baseline 必须是 resolved `VERIFIED` commit；Capability Baseline 必须绑定 resolved current-ref/full SHA。关键证据缺失时输出对应 Gap，不在本 Skill 内重建。
5. 会改变边界、所有权、状态机、一致性或外部合同时，缺少权威依据必须 `BLOCKED`；不要用当前实现或假设补齐。
6. 先裁决复用、配置、组合、扩展、包装、升级、新公共能力或局部实现；不要在盘点现有能力前平行建设机制。
7. 从 required guarantee 和 failure model 选择机制，再选择当前 stack 下的实现；不要从类名、框架 API 或案例关键词倒推模型。
8. 未经明确要求不要覆盖已有 Spec。不得把机械开发任务、中央治理变更或 As-Is 更新混入产物。

## Context Profiles

先确定本次涉及的 profile，只完整读取命中的文件。不要因关键词相似预加载全部标准。

| Profile | Trigger | Read |
|---|---|---|
| `CORE` | 任何 Spec 设计 | `../_standards/explicit-architecture/core-standard.md` |
| `FULL_PACKAGE` | 新建完整分层 Spec，或修改跨越多个层 | `references/generation-playbook.md` |
| `GOVERNED_CONTEXT` | 使用中央上下文、Service/Capability Baseline 或 Pending Delta | `../_standards/explicit-architecture/architecture-context-contract.md`、`../_standards/explicit-architecture/as-is-consumption-contract.md`、`../_standards/explicit-architecture/capability-as-is-consumption-contract.md`、`../_standards/explicit-architecture/baseline-ref-profile.md` |
| `DISCOVERY` | 判断共享能力 fit/gap 或外部 contract/process readiness | `../_standards/explicit-architecture/discovery-gates.md` |
| `DECISION` | decision 会改变边界、权威、状态机、保障、公共能力、外部合同或高代价演进 | `../_standards/explicit-architecture/decision-framework.md` |
| `MECHANISM` | 一致性、并发、事务、异步副作用、重试、外部交互、长流程或技术派生状态 | `../_standards/explicit-architecture/mechanism-selection.md` |
| `ARTIFACT_EVOLUTION` | 代码级 Target 产物、Mapper、Brownfield、兼容、迁移或过渡 | `../_standards/explicit-architecture/target-artifacts-and-mapping.md`、`../_standards/explicit-architecture/target-evolution-and-verification.md` |
| `TECHNOLOGY` | 选择具体语言、框架、存储或消息实现 | `../_standards/explicit-architecture/technology-selection.md` |
| `GENERALIZATION` | shared/seedwork 扩展、公共能力或 Shared Kernel | `../_standards/explicit-architecture/capability-generalization.md` |
| `RULE_IDS` | 输出精确标准 ID | `../_standards/explicit-architecture/rule-index.md` |

进入对应判断前完整读取该 profile 的文件。引用规则 ID 前必须读取 `rule-index.md`。

## Workflow

### 1. 固定 Scope 与 Authority

记录 goal、in/out of scope、需求来源、中央 index/ref、适用文档及 authority、批准例外、Ref Profile、Baseline、Pending Delta、external evidence 和实际写入路径。

中央索引或适用资料缺失、冲突、过期或不可访问时输出 `ARCHITECTURE_CONTEXT_GAP`。中央仓库始终只读。

### 2. 建立 Current Inputs

验证并摘录 Service As-Is、feature merge-base/Pending Delta、Capability owner baseline 与服务实际采用情况、外部 contract/process/readiness，以及 effective technology stack。每项都绑定其 resolved ref/version、scope 和证据时间。

分别输出 `BASELINE_GAP`、`CAPABILITY_BASELINE_GAP` 或 `ARCHITECTURE_CONTEXT_GAP`；不要临时重建后冒充 canonical input。

### 3. 建立需求与流程骨架

为需求、业务规则和验收标准分配稳定 ID。确认参与者、系统边界、端到端正常/拒绝/取消/失败/未知/恢复路径，以及事实和状态 authority。

建立追踪链：

```text
Requirement
  → Domain meaning/decision
  → Application orchestration
  → Interface/Infrastructure/Integration guarantee
  → Verification
  → Current-to-Target action
```

### 4. 形成并裁决方案

不要直接从 requirement 跳到类、表或框架方案。先建立 Decision Inventory；对每项非平凡决定加载 `DECISION` profile，并按共享 `decision-framework.md` 的“方案形成与裁决回路”执行：

```text
Decision Contract
  → materially different candidates
  → hard reject gates
  → ordered quality decision
  → standard-path / counterfactual / evolution attacks
  → selected and rejected evidence
```

只有一个候选可行时记录可验证的排除依据，不制造假选项。记录 selected/rejected rationale、residual risk、fallback、invalidation condition 和 verification；共享机制 fit/gap、具体机制族和技术实现分别通过 `DISCOVERY`、`MECHANISM` 和 `TECHNOLOGY` profile 裁决。

先完成技术中立的 fit/gap 与机制族裁决，再根据 effective stack 生成 implementation candidates。版本不明时保留 Target intent，并将具体实现标记为 `BLOCKED` 或 `UNKNOWN`。

### 5. 组装选中方案与 Artifact Budget

将选中方案转换为唯一责任链：

```text
Business meaning
  → Decision owner
  → Required outer guarantee
  → Mechanism owner
  → Operational recovery
  → Verification evidence
```

先登记现有可 `KEEP/MODIFY` 的产物，再登记有需求依据的 `ADD`。为每个产物标记 natural owner、layer、type、source 和 change action；执行 information/lifecycle/boundary/deletion tests。没有独立信息或责任的产物不得进入 Target。

### 6. 从内向外设计

按顺序设计：

1. **Domain**：统一语言、Aggregate、行为、不变量、业务状态、Domain Event、外部业务引用和写侧 Persistence 语义。
2. **Application**：Command/Query/Result、Handler 编排、Port、Mapper contract、原子性意图、I/O 顺序和真正的长流程协调。
3. **Interfaces**：当前协议所需 Request/Response/Message、Driving Adapter、Boundary Mapper、协议校验和稳定错误。
4. **Infrastructure**：Persistence Model、Adapter/Mapper、约束、事务、技术派生字段、Durable Intent、Retry、Idempotency、Client、migration 和技术异常翻译。
5. **Integration**：达到 readiness 后再设计 contract/schema owner、兼容、投递、幂等、顺序、状态映射、timeout、compensation 和 reconciliation。

保持内层技术纯净。让 Mapper 只转换、Handler 只编排、外层明确履行内层约束；不要创建无独立职责的抽象。

### 7. 挑战、优化并设计 Verification / Evolution

重放共享框架的 standard-path、failure-boundary、counterfactual、authority、evolution 和 deletion attacks。失败时回到 Decision Contract 或候选比较；通过后才删除重复状态、判断和无独立职责的产物。

让 Verification 覆盖 Domain behavior、Handler orchestration、Mapper special cases、Adapter guarantee、contract compatibility、并发、failure injection、recovery、migration 和 rollback，并证明选中机制实际承诺的 scope/effective point。

Brownfield 必须记录 Current Evidence、Target Artifact、`KEEP/MODIFY/ADD/MOVE/RENAME/REPLACE/DELETE`、兼容策略、阶段 gate、回滚点以及过渡产物删除条件。先确认 Target，再设计演进。

### 8. 写入并验证

优先遵守仓库已有 canonical Spec 结构；没有约定时使用 `assets/spec-template/`。

替换全部 placeholder，删除不适用的可选文档和章节，不创建空层，不复制模板说明。同一事实只保留一个权威表达，其他位置使用引用。流程、状态、时序和关系用合适的 Mermaid；属性、约束、映射和追踪用表格；依据与风险用短文。

使用默认结构时运行：

```bash
python3 "<skills-root>/_standards/explicit-architecture/scripts/validate_package.py" \
  --profile spec --layout template "<spec-root>"
```

使用既有 canonical 结构时改用 `--layout existing`。任何 `[ERROR]` 都禁止声明完成；逐项处理或输出 `BLOCKED`。解释仍保留的 `[WARN]`。

## Semantic Completion Gate

在 validator 通过后再确认：

- 需求、设计、验证和演进追踪闭合；
- authority 类型、Baseline 和 Pending Delta 没有混淆；
- 每项非平凡 decision 有共享框架要求的 Design Contract、实质候选比较或可验证的单候选理由；
- 选中方案通过共享硬门禁，并记录拒绝理由、residual risk、fallback 和失效条件；
- Domain 不含协议、持久化框架、消息产品、Retry 或技术派生状态；
- 每个关键语义有唯一 decision owner 和 final guarantee；
- 外部状态、重复、乱序、timeout、结果未知和恢复均有依据；
- 方案通过第二入口、并发、崩溃、重复、乱序和版本共存的相关反事实测试；
- 公共能力 fit/gap、服务采用事实和 consumer impact 已裁决；
- Target 每个产物有需求来源和演进动作；
- 未决问题没有被假设掩盖。

## Result

返回：

- `PASS`：结构验证和语义门禁均通过；
- `PASS WITH ASSUMPTIONS`：假设明确且不改变关键边界；
- `BLOCKED`：缺失依据会改变关键设计，或 validator 仍有 error。

同时列出 Baseline/Context Gap、As-Is Delta Candidate 和 `Architecture Governance Candidates`；没有候选时明确写 `None`。不要在本流程中执行这些后续变更。
