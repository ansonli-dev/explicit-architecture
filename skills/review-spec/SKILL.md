---
name: review-spec
description: Review a developer-authored feature specification against requirements, a read-only central Architecture Context, a verified service As-Is baseline, relevant shared-capability owner baselines, pending deltas, approved decisions, and external contracts. Use when asked to approve, audit, critique, or provide revision guidance on a proposed feature spec, RFC, or ADR-backed design without rewriting it, reviewing implementation code, curating central architecture documents, or updating any As-Is.
---

# 审查显式架构 Spec

审查 developer 提交的 Spec 是否依据充分、方案合理、责任收敛且可安全演进。审查设计和证据，不审排版，也不代写 Target。

## Hard Gates

1. 默认只给定点修改建议；未经明确要求不改 Spec、不写生产代码。
2. 中央 Architecture Context 与 Canonical Service/Capability As-Is 始终只读；不在 review 中创建、重建或更新。
3. 固定实际使用的 repository、immutable commit/version、Ref Profile、environment 和 evidence timestamp；移动分支名不能代替 SHA。
4. 区分 mandatory Standard、advisory Pattern、Current As-Is、approved Target 和代码证据；当前实现不自动等于 Target。
5. 会改变边界、所有权、状态机、一致性或外部合同的关键依据缺失时，结论必须 `BLOCKED`。
6. 审查“为何选择”而不只审查“能否实现”；不要因为存在比较表就默认方案合理。
7. finding 必须可定位、有依据、有后果和可执行的修订方向；不要为展示严格制造意见。

## Context Profiles

始终完整读取 `CORE`；其余只读取实际命中的 profile。

| Profile | Trigger | Read |
|---|---|---|
| `CORE` | 任何 Spec review | `../_standards/explicit-architecture/core-standard.md`、`references/spec-rubric.md` |
| `GOVERNED_CONTEXT` | 使用中央上下文、Service/Capability Baseline 或 Pending Delta | `../_standards/explicit-architecture/architecture-context-contract.md`、`../_standards/explicit-architecture/as-is-consumption-contract.md`、`../_standards/explicit-architecture/capability-as-is-consumption-contract.md`、`../_standards/explicit-architecture/baseline-ref-profile.md` |
| `DISCOVERY` | 共享能力 fit/gap 或外部 contract/process readiness | `../_standards/explicit-architecture/discovery-gates.md` |
| `DECISION` | 非平凡架构决定、方案裁决或反事实测试 | `../_standards/explicit-architecture/decision-framework.md` |
| `MECHANISM` | 一致性、并发、事务、异步副作用、重试、外部交互、长流程或技术派生状态 | `../_standards/explicit-architecture/mechanism-selection.md` |
| `ARTIFACT_EVOLUTION` | Target 产物、Mapper、Brownfield、兼容、迁移或过渡 | `../_standards/explicit-architecture/target-artifacts-and-mapping.md`、`../_standards/explicit-architecture/target-evolution-and-verification.md` |
| `TECHNOLOGY` | 具体 stack、版本或候选实现 | `../_standards/explicit-architecture/technology-selection.md` |
| `GENERALIZATION` | shared/seedwork 扩展、公共能力、复用或 Shared Kernel | `../_standards/explicit-architecture/capability-generalization.md` |
| `RULE_IDS` | finding 引用精确标准 ID | `../_standards/explicit-architecture/rule-index.md` |

进入对应判断前完整读取该 profile。引用规则 ID 前必须读取 `rule-index.md`。

按以下顺序解析权威：

1. 用户对本次审查明确给出的 Target 决定；
2. 适用于当前 scope 的 canonical Architecture、Standard 与 ADR；
3. 已接受 ADR、上游 Spec 和外部合同；
4. 当前待审 Spec；
5. 当前实现证据。

Spec 与更高权威冲突时报告 finding。Target 已有安全演进路径时，不因它“不像当前代码”而否决。

## Workflow

### 1. 固定审查范围与证据

识别主 Spec、需求/验收标准、ADR、API/event/schema contract、migration/rollout，以及引用但未提供的材料。只读取验证设计依据和演进可行性所需的代码；需要无边界实现扫描时列为后续事项。

固定中央 context、Service/Capability Baseline、development ref/Pending Delta、feature merge-base、external evidence 和 effective stack。分别报告 `ARCHITECTURE_CONTEXT_GAP`、`BASELINE_GAP` 或 `CAPABILITY_BASELINE_GAP`；不要用当前代码或假设补齐。

### 2. 建立追踪骨架

提取 requirement、business rule、acceptance criteria、flow、fact authority、Design Contract、candidate/verdict、分层责任、verification 和 evolution action，建立：

```text
Requirement
  → Domain meaning/decision
  → Application orchestration
  → Interface/Infrastructure/Integration guarantee
  → Verification
  → Current-to-Target action
```

必要环节缺失、冲突、重复保障或无人保障，形成候选 finding。

默认 Spec package 先运行：

```bash
python3 "<skills-root>/_standards/explicit-architecture/scripts/validate_package.py" \
  --profile spec --layout template "<spec-root>"
```

既有 canonical 结构改用 `--layout existing`。把 error 转成可定位 finding；validator 不替代语义审查。

### 3. 审查 Context 与流程依据

执行 rubric 的 `Context Gate`、`Capability Gate` 和适用的 `Integration Gate`。确认 authority、Baseline/Pending Delta、共享能力采用事实、正常/拒绝/取消/失败/未知/恢复路径，以及外部 contract/process/readiness 均有证据。

未决信息会改变 Aggregate、状态机、所有权、contract 或 compensation 时报告 `BLOCKER`；不要用 client 行为、供应商印象或未验证版本能力填补。

### 4. 独立复核方案裁决

对非平凡决定加载 `DECISION`，按共享 `decision-framework.md` 的“方案形成与裁决回路”复核：

1. Design Contract 是否覆盖目标、权威、范围/时点、失败、Current/evolution constraints 和 required evidence；
2. 是否存在实质候选或可验证的单候选理由，而非只改命名、API 或参数；
3. `SELECTED` 是否通过硬门禁，关键 `UNKNOWN` 是否被错误降级；
4. 存活候选是否按共享质量顺序裁决，而非用总分、代码量或熟悉度掩盖缺陷；
5. 独立重放相关 standard-path、failure-boundary、counterfactual、authority、evolution 和 deletion attacks 后是否仍成立；
6. 是否记录 selected/rejected rationale、residual risk、fallback、invalidation condition 和 verification。

简单、可逆的局部决定不必有完整 ADR。关键证据不足通常是 `BLOCKER`；依据充分但裁决错误通常是 `MAJOR`。

### 5. 从内向外审查并收敛

按 rubric 的 `Domain → Application → Interfaces → Infrastructure → Integration → Verification` Gates 顺序检查；只在所属层要求该层内容。

对每条关键规则验证唯一责任链：

```text
Business meaning
  → Decision owner
  → Required outer guarantee
  → Mechanism owner
  → Operational recovery
  → Verification evidence
```

重点识别内层技术污染、外层保障缺失、重复事实/判断、隐藏顺序、错误状态复制、无职责抽象、现有能力的平行建设，以及未经版本证据支持的实现选择。逐项问题以 rubric 和命中 profile 为准，不在主流程复制检查清单。

### 6. 审查 Evolution 与 Verification

执行 rubric 的 `Evolution Gate` 和 `Verification Gate`。Brownfield 必须把 Current Evidence 与 Target 分开，记录 `KEEP/MODIFY/ADD/MOVE/RENAME/REPLACE/DELETE`、兼容/迁移/回滚、过渡产物删除条件和 shared-capability consumer impact。

验证证据必须覆盖选中机制承诺的 scope/effective point 和适用 failure；Mock 或 happy path 不能证明并发、原子性、幂等、恢复或兼容。

### 7. 验证 Findings

对每条候选 finding 执行 rubric 的 `Finding Gate`。保持在 Spec 层；短示例可以帮助定位，但不要用大段生产代码或重写章节代替建议。

## Result

先按严重度输出 findings：

```text
[MAJOR] 标题
Location: Spec 文件与章节
Issue: 缺失、冲突或不合理的设计
Why it matters: 对业务、架构或演进的影响
Suggested revision: 应补充、移动、删除或重新裁决什么
Trace: requirement / `CTX-*` / `ASIS-*` / `CASIS-*` / `DISC-*` / `DR-*` / `MECH-*` / `GEN-*` / `TECH-*` / target standard
```

- `BLOCKER`：缺失依据会实质改变关键设计，无法可靠批准；
- `MAJOR`：错误归属、关键遗漏、脆弱机制或不可执行演进；
- `MINOR`：不改变核心方案的精度或局部一致性问题；
- `INSIGHT`：公共能力或治理机会，不自动阻塞当前 Spec。

最后给出 `Decision: APPROVE | APPROVE WITH COMMENTS | REQUEST CHANGES | BLOCKED`、阻塞批准的最小修订、non-blocking insights、Architecture governance candidates 和 review coverage。没有 finding 时明确批准；不要制造修改意见。

## Boundaries

- 不从需求生成新 Spec，也不把 review 变成重写或代码审查。
- 不因缺少不适用的层或产物扣分；可信说明不适用即可。
- 不要求机械开发任务，不把迁移妥协推入最终 Domain。
- 不创建、修订或提升 Canonical As-Is，不修改中央架构仓库。
- 不在一次 review 中无边界扩张公共能力实现范围。
