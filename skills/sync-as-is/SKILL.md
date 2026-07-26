---
name: sync-as-is
description: Build, reconstruct, verify, and promote a layered As-Is architecture baseline for a deployable business service from repository, schema, configuration, contract, deployment, and runtime evidence while treating central architecture and shared-capability owner repositories as read-only inputs. Use when asked to document the current service, inventory its actual shared-capability adoption, recover brownfield service architecture, compare maintained service As-Is with implementation/runtime, prepare an As-Is delta from configured development or feature refs, or promote a verified candidate commit; do not use for shared library/SDK capability baselines, central architecture curation, future feature design, or code-quality review.
---

# 同步业务服务 As-Is 架构资产

维护“可部署业务服务现在真实如何运行”的可验证资产。Service As-Is 绑定 immutable commit、environment 和 evidence；不把公共能力 Owner 契约、Target、计划或未经验证的实现写成当前事实。

## 标准读取路由

开始前完整读取最小工作集：

- `../_standards/explicit-architecture/as-is-consumption-contract.md`：Feature Delivery Skill 的 Baseline、Delta 和写入契约；
- `../_standards/explicit-architecture/baseline-ref-profile.md`：解析实际 verification/development/feature ref role；
- `references/as-is-lifecycle.md`：Baseline、Delta、漂移和提升；
- `references/as-is-playbook.md`：四种 mode、Git 取证和更新门禁。

根据实际证据范围再完整读取：

| Trigger | Read |
|---|---|
| 服务消费公共能力 | `../_standards/explicit-architecture/capability-as-is-consumption-contract.md`、`references/evidence-discovery.md` |
| 中央 Architecture Context 可见或需要治理候选 | `../_standards/explicit-architecture/architecture-context-contract.md` |
| 共享能力 fit/gap 或外部 contract/process evidence | `../_standards/explicit-architecture/discovery-gates.md`、`references/evidence-discovery.md` |
| 盘点会影响后续 Target 的有效 stack/version/environment | `references/technology-inventory.md` |
| 复杂可靠性机制 | `../_standards/explicit-architecture/mechanism-selection.md`，只描述实际 guarantee |
| Shared Capability 或 Shared Kernel 分类 | `../_standards/explicit-architecture/capability-generalization.md`，只分类 Current |
| 输出精确共享规则 ID | `../_standards/explicit-architecture/rule-index.md` |

未触发的标准不要预加载。触发后必须完整读取对应文件；不要读取 Target 核心、产物、技术选择或演进标准来反向整理现实。

需要判断偏差时只把仓库已批准的 Target 标准作为被引用的 authority。

## 工作边界

开始时执行仓库角色门禁：

- 当前 scope 必须是可部署业务服务、bounded context 或其运行组成；
- 共享库、内部 SDK、Starter、Framework Module 和中央 Architecture Context 不在本 Skill 范围内；
- 混合仓库只扫描明确的业务服务 scope，不把共享发布单元并入服务分层模板。

本 skill：

- 创建、重建、验证和更新分层 As-Is；
- 按适用性读取中央 Architecture、Standards、Patterns、Landscape 和 ADR；
- 建立服务的 Shared Capability Consumption Snapshot 和 External Integration Dossier；
- 比较 As-Is 与 code/schema/config/contract/runtime；
- 生成 development、feature 或 verification/candidate ref 的 As-Is Delta；
- 在测试验证通过后提升新的 Canonical As-Is。

本 skill 不：

- 设计待开发功能；
- 生成 Feature Spec 或 implementation plan；
- 审批 Spec；
- 判断代码方案是否优雅或符合 Target；
- 修改生产代码；
- 创建、移动、整理或修改中央架构仓库的文档；
- 创建、修订或提升公共能力 Owner 的 Capability As-Is；
- 用未经批准的实现覆盖 As-Is。

## 中央 Architecture Context

中央仓库可见时：

1. 固定 repository identity、immutable commit/version 和 index version；
2. 先读索引，根据当前服务、能力、协议和环境选择 `ACTIVE` 资料；
3. 将 `ARCHITECTURE / STANDARD / ADR` 作为适用的 Target authority；
4. 将 `PATTERN` 作为参考，不用它反向美化 Current；
5. 将 `LANDSCAPE / CATALOG` 作为 owner 和证据入口；
6. 精确能力契约回到 Capability As-Is，实际采用版本和配置回到服务 As-Is。

本 Skill 对中央仓库始终只读。发现缺失、冲突、过期、适用范围不清或中央摘要与自然 owner 不一致时，输出 `Architecture Governance Candidate`，不顺带修文档。

## Mode

开始时选择一个且仅一个：

### `GREENFIELD_BOOTSTRAP`

建立初始事实资产：

- system landscape 和 ownership；
- 服务已采用的 platform/shared capabilities；
- technology/runtime constraints；
- external contract/process；
- 尚不存在的业务实现明确标记 `NOT_IMPLEMENTED`。

### `BROWNFIELD_RECONSTRUCT`

As-Is 不存在或不可信时，从实际证据恢复：

- actual dependency 和 runtime path；
- layered artifact；
- consumed capability/integration；
- confidence、unknown 和 known deviation。

不把现状整理成理想架构。

### `BASELINE_VERIFY`

As-Is 已维护时，比较绑定 Baseline 与实际证据：

- `NO_DRIFT`
- `IMPLEMENTATION_DRIFT`
- `AS_IS_STALE`
- `APPROVED_CHANGE`
- `TRANSITION_STATE`
- `ENVIRONMENT_DRIFT`
- `EVIDENCE_GAP`

只在用户要求更新且证据门禁通过时修订 As-Is；否则输出 Drift Report。

### `POST_DELIVERY_PROMOTE`

将 resolved verification/candidate ref 上测试通过的 SHA 提升为新 Canonical As-Is；默认 Profile 对应 `test@sha`。必须同时有：

- 上一 `VERIFIED` Baseline；
- 新 verification/candidate-ref commit；
- approved Spec/ADR；
- code/schema/config/contract evidence；
- candidate verification evidence；
- environment identity。

缺任一关键证据则 `BLOCKED`。

## Branch Profile

先按 `baseline-ref-profile.md` 解析实际 ref role。默认使用：

```text
Canonical As-Is      = last VERIFIED verification-ref@sha
Pending Develop Delta = verified baseline → development-ref@sha
Feature Delta         = actual merge-base → feature@sha
Test Candidate Delta  = verified baseline → unverified verification/candidate-ref@sha
```

所有 ref 固定为 SHA。读取其他分支时优先使用 `git show`、`git diff`、`git merge-base` 或独立 worktree；不要为了扫描切换、合并或改写用户当前分支。

当前仓库分支名与默认 Profile 不同时，读取 canonical repository workflow 或用户显式 Profile。记录 `profile_id`、source、resolved ref 和 full SHA；Profile 只改变 ref 映射，不放宽验证或提升门禁。

需要创建独立 As-Is 工作分支时，从 resolved verified Baseline SHA 派生；除非用户明确要求，不自动创建、切换或合并分支。

## 工作流

### 1. 固定 Scope 与 Evidence Ref

记录：

- mode；
- system/module/integration scope；
- baseline branch、commit、environment、status；
- development/feature/verification candidate SHA；
- merge-base；
- evidence timestamp；
- unavailable evidence。

分支名存在但 commit 或验证状态未知时，不声称已固定 Baseline。

### 2. 建立 Evidence Map

按需读取：

- repository instructions、canonical standards、ADR 和已批准 Spec；
- 中央 Architecture Context 的 index、适用文档 ID、commit/version 和 authority；
- code、build、resolved dependency 和 module graph；
- schema、migration、data constraint；
- application/deployment configuration；
- contract/schema/registry 和 external process；
- runtime topology、observability 和 operational procedure；
- tests、DC/test verification evidence；
- Capability As-Is 的权威入口；
- 当前服务的 resolved capability version、配置和本地扩展。

区分 `CONFIRMED / INFERRED / UNKNOWN`。代码只能证明实现形状，不能单独证明外部合同或运行环境。

### 3. 生成分层 As-Is

使用 `assets/as-is-template/`：

- `00-baseline.md`
- `01-landscape.md`
- `02-shared-capabilities.md`
- `systems/system/01-domain.md`
- `systems/system/02-application.md`
- `systems/system/03-interfaces.md`
- `systems/system/04-infrastructure.md`
- `systems/system/05-runtime.md`
- `integrations/external-system.md`
- `traceability.md`

只创建适用文档，替换 `system` 和 `external-system`。`02-shared-capabilities.md` 只记录当前服务的采用事实并链接 Capability As-Is，不重建 Owner 契约。每项事实包含 evidence、ref、environment 和 confidence。

### 4. 比较并分类 Delta

对每个差异记录：

- baseline fact；
- observed fact；
- approved intent；
- classification；
- affected layer/capability/contract；
- evidence；
- action owner。

本 Skill 只确定哪种事实关系成立：

- `AS_IS_STALE` 可更新资产；
- `APPROVED_CHANGE` 等待验证和提升；
- `IMPLEMENTATION_DRIFT` 只报告为实现或批准设计不一致，不在本 Skill 中评价或修复；
- `EVIDENCE_GAP` 不猜测。

中央上下文可能过期或不适用时，不把差异直接分类为实现错误；输出 `Architecture Governance Candidate`，不修改中央仓库。

### 5. 写入或提升

创建/更新前确认：

- 用户要求写入 As-Is；
- 目标目录符合仓库规范；
- 没有覆盖其他 baseline/environment；
- Canonical 更新满足 `ASIS-05`；
- 过渡项有 owner 和删除条件；
- Manifest 中使用 full SHA；
- 新 Baseline 链接 superseded Baseline。

不得因为 development ref 的 DC 通过直接更新 Canonical As-Is。DC 通过只生成 Candidate Delta；resolved candidate 验证通过后才提升。

## 产出位置

优先遵守仓库已有 As-Is 目录。没有约定时使用：

```text
docs/architecture/as-is/
├── 00-baseline.md
├── 01-landscape.md
├── 02-shared-capabilities.md
├── systems/{system}/
├── integrations/{external-system}.md
└── traceability.md
```

不要覆盖其他 environment 或 Baseline；不要创建空层。

## 输出

按 mode 输出：

### Bootstrap / Reconstruct

- `Baseline status`
- `Created scope`
- `Evidence coverage`
- `Unknowns`
- `Known deviations`

### Verify

- findings 按 drift classification 分组；
- `Verdict: IN_SYNC | DRIFT_FOUND | EVIDENCE_INCOMPLETE | BLOCKED`；
- 未经要求不修改文件。

### Promote

- old/new Baseline ID 和 SHA；
- promoted layers/capabilities/contracts；
- remaining transitions/gaps；
- verification evidence；
- `Promotion: COMPLETED | BLOCKED`。

## 输出契约

### Service Baseline Package

- Baseline ID、Ref Profile、resolved verification ref/full SHA、environment 和 verification status；
- relevant layered facts、capability adoption 和 integrations；
- Pending Develop/Feature Delta；
- approved/pending decisions；
- evidence、confidence 和 gaps。

### Gap and Candidate

- `BASELINE_GAP`
- `As-Is Delta Candidate`
- `Architecture Governance Candidate`

这些字段是其他产物可消费的稳定接口；本 Skill 不规定后续使用流程。
