---
name: sync-as-is
description: Build, reconstruct, verify, and promote a layered As-Is architecture baseline for a deployable business service from repository, schema, configuration, contract, deployment, and runtime evidence while treating central architecture and shared-capability owner repositories as read-only inputs. Use when asked to document the current service, inventory its actual shared-capability adoption, recover brownfield service architecture, compare maintained service As-Is with implementation/runtime, prepare an As-Is delta from configured development or feature refs, or promote a verified candidate commit; do not use for shared library/SDK capability baselines, central architecture curation, future feature design, or code-quality review.
---

# 同步业务服务 As-Is

维护“特定环境和 immutable commit 上真实如何运行”的可验证事实资产。记录实际结构和偏差，不把 Current 美化成 Target。

## Hard Gates

始终满足：

1. 只处理可部署业务服务、bounded context 或其运行组成；共享库、SDK、Starter、Framework Module 和中央架构仓库不在范围内。
2. 只描述 Current、Delta 和已验证提升；不设计未来功能，不评价实现优雅度，不生成 Spec 或 implementation plan。
3. 对中央 Architecture Context 和 Capability Owner 仓库始终只读；只链接权威契约，不复制或修改其事实资产。
4. Canonical Service As-Is 必须绑定 Ref Profile、resolved verification ref/full SHA、environment、verification status、evidence timestamp 和 supersedes。
5. 不把 branch name、working tree、development/feature ref 或未验证 candidate 当作 Canonical Current。
6. 区分 `CONFIRMED / INFERRED / UNKNOWN`；代码形状不能单独证明外部合同、运行环境或公共能力 guarantee。
7. 只有用户明确要求写入且对应 Mode 门禁通过时，才创建、更新或提升 As-Is；否则只报告事实和 Delta。
8. 不修改生产代码、schema、配置或中央治理文档。发现设计问题只分类事实关系，并交给对应 owner。

## Mode

选择一个且仅一个：

| Mode | Use | Write rule |
|---|---|---|
| `GREENFIELD_BOOTSTRAP` | 新系统尚无可信 Baseline | 记录已存在事实；未实现内容标记 `NOT_IMPLEMENTED` |
| `BROWNFIELD_RECONSTRUCT` | 既有服务缺少或不可信 As-Is | 从固定证据恢复 Current，不进行理想化重构 |
| `BASELINE_VERIFY` | 比较维护中的 As-Is 与绑定证据 | 默认只读；用户要求且 `AS_IS_STALE` 证据充分时才更新 |
| `POST_DELIVERY_PROMOTE` | 将已测试 candidate 提升为新 Canonical Baseline | 缺 approved decision、部署/测试或生效证据时 `BLOCKED` |

不要在同一次运行中重建未知 previous state 并宣称完成 Promotion。

## Context Profiles

先选择最小 profile，只在进入对应操作前完整读取：

| Profile | Trigger | Read |
|---|---|---|
| `CORE` | 任何 Service As-Is 操作 | `../_standards/explicit-architecture/as-is-consumption-contract.md`、`../_standards/explicit-architecture/baseline-ref-profile.md` |
| `EVIDENCE_OPERATION` | Bootstrap、Reconstruct 或 Verify | `references/as-is-playbook.md` |
| `LIFECYCLE_WRITE` | 生成 Candidate Delta、更新 Baseline 或 Promote | `references/as-is-lifecycle.md` |
| `SHARED_CAPABILITY` | 服务消费公共能力 | `../_standards/explicit-architecture/capability-as-is-consumption-contract.md`、`references/evidence-discovery.md` |
| `GOVERNED_CONTEXT` | 中央 Architecture Context 可见或需要治理候选 | `../_standards/explicit-architecture/architecture-context-contract.md` |
| `DISCOVERY` | 共享能力 fit/gap 或外部 contract/process evidence | `../_standards/explicit-architecture/discovery-gates.md`、`references/evidence-discovery.md` |
| `TECH_INVENTORY` | 盘点会影响后续 Target 的有效 stack/version/environment | `references/technology-inventory.md` |
| `RELIABILITY` | 描述复杂一致性、投递、幂等、重试或恢复 guarantee | `../_standards/explicit-architecture/mechanism-selection.md`，只记录实际承诺 |
| `GENERALIZATION` | 分类 Shared Capability 或 Shared Kernel | `../_standards/explicit-architecture/capability-generalization.md`，只分类 Current |
| `RULE_IDS` | 输出精确规则 ID | `../_standards/explicit-architecture/rule-index.md` |

不要读取 Target 产物、技术选择或演进标准来反向整理现实。

## Ref Views

先按 `baseline-ref-profile.md` 解析仓库实际角色，再固定 full SHA。默认 Profile 表达：

```text
Canonical As-Is       = last VERIFIED verification-ref@sha
Pending Develop Delta = verified baseline → development-ref@sha
Feature Delta         = actual merge-base → feature@sha
Test Candidate Delta  = verified baseline → unverified candidate@sha
```

优先使用 `git show`、`git diff`、`git merge-base` 或独立临时 worktree 读取其他 ref。不要为扫描切换、合并、rebase、reset 或改写用户当前分支；不要自动 fetch 或假设 remote 已同步。

## Workflow

### 1. 固定 Scope 与 Evidence Refs

记录 Mode、service/system scope、repository、Ref Profile source、resolved refs/full SHA、merge-base、environment、status、evidence timestamp、允许写入路径和 unavailable evidence。

无法解析所需 commit 或 verification state 时 `BLOCKED`。共同上级目录不等于同一个 Git repository；逐个读取 repository rules 和 ref。

### 2. 建立 Evidence Map

按风险读取：

- repository instructions、build/module/dependency graph；
- code、schema、migration、constraint 和 tests；
- deployment configuration、runtime topology、observability 和 operational procedure；
- approved Spec/ADR 和 verification evidence；
- 中央 index、适用 document IDs、authority 和 applicability；
- Capability As-Is owner baseline、服务 resolved version/configuration/extension；
- external contract/schema/process、callback/query/recovery 和 environment 差异。

对每项事实记录最窄充分证据、commit/environment 和 confidence。未找到不等于不存在；缺少权威证据时标记 `EVIDENCE_GAP`。

### 3. 生成或核验分层 Current

按实际 vertical slice 追踪：

```text
input
  → boundary mapping
  → use-case orchestration
  → business decision
  → persistence/outbound mechanism
  → result/effect
```

记录 Landscape、Consumed Shared Capabilities、Domain、Application、Interfaces、Infrastructure、Runtime、Integration 和 traceability。只创建有证据的层；若职责实际放错位置，如实记录位置和已知偏差，不改写成理想结构。

### 4. 比较并分类 Delta

比较 Existing As-Is、bound code/runtime、approved Spec/ADR 和 verification evidence，只使用：

- `NO_DRIFT`
- `IMPLEMENTATION_DRIFT`
- `AS_IS_STALE`
- `APPROVED_CHANGE`
- `TRANSITION_STATE`
- `ENVIRONMENT_DRIFT`
- `EVIDENCE_GAP`

对每项差异记录 baseline fact、observed fact、approved intent、classification、affected layer/capability/contract、evidence 和 action owner。当前代码存在不能自动判定 `AS_IS_STALE`。

中央上下文缺失、冲突、过期或适用性不明时输出 `Architecture Governance Candidate`，不要把差异直接裁决为实现错误。

### 5. 写入或 Promote

写入前确认：

- 用户明确要求修改 As-Is；
- 目标目录符合仓库规范且不会覆盖其他 Baseline/environment；
- 所有事实来自已固定 ref 和声明 environment；
- Manifest、traceability、supersedes 和 Transition 删除条件同步更新；
- Canonical 更新满足 `ASIS-05`；
- Promotion 具备 previous `VERIFIED` Baseline、immutable candidate、approved Spec/ADR、部署/测试、schema/config/contract 和 runtime evidence。

任何关键 Promotion 证据缺失时保留上一 Canonical Baseline，并返回 `BLOCKED`。

## Package Contract

优先遵守仓库已有 canonical As-Is 结构。没有约定时使用 `assets/as-is-template/`：

```text
docs/architecture/as-is/
├── 00-baseline.md
├── 01-landscape.md
├── 02-shared-capabilities.md      # when applicable
├── systems/{system}/             # only evidenced layers
├── integrations/{external}.md    # when applicable
└── traceability.md
```

替换全部 placeholder，不创建空层，不覆盖其他 environment。`02-shared-capabilities.md` 只记录服务采用事实并链接 Capability As-Is，不重建 owner contract。

使用默认结构时运行：

```bash
python3 "<skills-root>/_standards/explicit-architecture/scripts/validate_package.py" \
  --profile service-as-is --layout template "<as-is-root>"
```

使用既有 canonical 结构时改用 `--layout existing`。任何 `[ERROR]` 都禁止声明完成或 Promotion；逐项处理或输出 `BLOCKED`。解释仍保留的 `[WARN]`。

## Semantic Completion Gate

在 validator 通过后再确认：

- Baseline、Candidate、Pending Develop Delta 和 Feature Delta 没有混淆；
- 重要事实均有 ref/environment、evidence 和 confidence；
- As-Is 如实记录 Current 和 deviation，没有 Target-only artifact；
- consumed capability 的 owner baseline 与服务实际采用事实已分离；
- external contract/process 与本地实现证据没有混为一体；
- Transition 有 owner、删除条件和期限；
- Promotion 有可定位的部署与测试证据；
- 中央 Architecture Context 保持只读，治理候选已单列。

## Result

按 Mode 返回：

- Bootstrap/Reconstruct：`Baseline status`、created scope、evidence coverage、unknowns 和 known deviations；
- Verify：`Verdict: IN_SYNC | DRIFT_FOUND | EVIDENCE_INCOMPLETE | BLOCKED` 和分类 findings；
- Promote：old/new Baseline ID 与 SHA、promoted scope、remaining transitions、verification evidence 和 `Promotion: COMPLETED | BLOCKED`。

同时返回稳定接口：`Service Baseline Package`、`BASELINE_GAP`、`As-Is Delta Candidate` 和 `Architecture Governance Candidate`。本 Skill 不执行这些候选的后续治理或实现工作。
