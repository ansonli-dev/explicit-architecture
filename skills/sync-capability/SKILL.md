---
name: sync-capability
description: Build, reconstruct, verify, and update a Capability As-Is baseline for a shared library, internal SDK, starter, or framework module using only the repository's configured stable capability Current ref (develop by default). Use when asked to document current shared capabilities, module structure, public contracts, guarantees, defaults, extension points, compatibility, consumers, or drift between maintained capability documentation and that stable ref; do not use for deployable business-service As-Is, future capability design, feature specifications, central architecture catalogs, or code-quality review.
---

# 同步公共能力 As-Is

维护“配置的稳定 Capability Current ref 当前提供什么公共能力”的可验证资产。默认 ref 为 `develop`；固定 `{capability-current-ref}@full-sha`，按 module 和 capability 记录事实，不引入独立发布流程，也不读取其他 ref 补充 Current。

## 标准读取路由

开始前完整读取最小工作集：

- `../_standards/explicit-architecture/capability-as-is-consumption-contract.md`：公共能力 Current 契约；
- `../_standards/explicit-architecture/baseline-ref-profile.md`：解析 Capability Current ref，默认 `develop`；
- `references/capability-lifecycle.md`：Baseline、状态、漂移和更新；
- `references/capability-evidence-playbook.md`：模块、契约、版本、兼容和消费者证据。

只在触发条件成立时完整读取：

| Trigger | Read |
|---|---|
| 识别能力边界或 Shared Kernel | `../_standards/explicit-architecture/capability-generalization.md`，只描述 Current |
| 描述一致性、投递、幂等、重试或恢复 guarantee | `../_standards/explicit-architecture/mechanism-selection.md`，只记录实际承诺 |
| 输出精确 `CASIS-*` ID | `../_standards/explicit-architecture/rule-index.md` |

未触发的标准不要预加载。触发后必须完整读取对应文件。

## Scope Gate

当前 scope 必须：

- 发布或提供可被其他代码库复用的模块；
- 对 public contract、默认行为和扩展点负责；
- 能用去业务化的 capability 语言描述。

可部署业务服务、中央架构仓库、未来方案设计和代码质量审查不在本 Skill 范围内。混合仓库只扫描明确的共享模块。

## 工作边界

本 Skill：

- 创建、重建、验证和更新 Capability As-Is；
- 建立 module map 和 capability catalog；
- 核验 public contract、guarantee、defaults、extension、compatibility 和 limitations；
- 比较文档与固定 `{capability-current-ref}@sha` 的代码、配置和测试证据；
- 输出 Baseline、Drift Report、Gap 和事实变化候选。

本 Skill 不：

- 读取 resolved current ref 之外的 feature/test/release ref 或 working tree 作为 Current；
- 维护业务服务分层、运行环境或实际采用配置；
- 设计新的公共能力或裁决 Target 方案；
- 审查实现是否优雅、值得公共化或符合业务仓库架构；
- 修改生产代码或中央架构仓库。

## Mode

选择一个：

- `GREENFIELD_BOOTSTRAP`：建立初始 Capability As-Is；
- `BROWNFIELD_RECONSTRUCT`：从 `{capability-current-ref}@sha` 恢复不可信或缺失的 Current；
- `BASELINE_VERIFY`：只读比较已维护 Baseline 与 `{capability-current-ref}@sha`；
- `BASELINE_UPDATE`：用户明确要求时，将文档更新到固定的 `{capability-current-ref}@sha`。

不要在同一次运行中用其他分支推测即将合入的能力。

## Branch Profile

```text
Canonical Capability As-Is = {capability-current-ref}@full-sha
Default current ref         = develop
```

先按 `baseline-ref-profile.md` 解析 ref。然后只解析已选中的 current ref，例如默认 Profile：

```text
git rev-parse --verify develop
git rev-parse develop^{commit}
```

仓库存在 remote/local 分支差异时，记录 Profile source 和实际解析来源；不自动 fetch，不假设 remote 已同步。读取其他 ref 不属于本 Skill。

## 工作流

### 1. 固定 Scope 与 Baseline

记录 repository、owner、module scope、Ref Profile、`{capability-current-ref}@full-sha`、status、evidence timestamp 和 unavailable evidence。

无法解析已选 current ref 的 commit 时 `BLOCKED`。只有 ref 名没有 SHA 时，不声称已固定 Baseline。

### 2. 建立 Evidence Map

只从固定 `{capability-current-ref}@sha` 按需读取：

- repository instructions 和 canonical module standards；
- build/module/dependency graph；
- module/artifact identity 和 effective version；
- public API/SPI/schema/configuration；
- defaults、error/failure semantics 和 extension points；
- state、migration、worker 和 operations（如存在）；
- contract、behavior、compatibility 和 migration tests；
- deprecation、replacement 和 consumer evidence。

区分 `CONFIRMED / INFERRED / UNKNOWN`。源码结构或类名不能单独证明 capability 和 guarantee。

### 3. 生成 Capability Current

使用 `assets/capability-as-is-template/`，按需创建：

- `00-baseline.md`
- `01-module-map.md`
- `02-capability-catalog.md`
- `capabilities/{capability}.md`
- `traceability.md`

先确定 module boundary，再按机制身份描述 capability。不要按 package/class 一对一生成能力，也不要复制完整 API Reference。

### 4. 比较并分类 Drift

对每项差异记录 Baseline fact、`{capability-current-ref}@sha` observed fact、批准依据、classification、affected capability/contract、evidence 和 owner。

只使用 `CASIS-05` 分类。`CONTRACT_GAP` 表示实现存在但 public contract、guarantee、defaults 或 extension 依据不足；它不是设计评价。

### 5. 写入

仅在用户要求创建或更新时写入，并确认：

- 所有事实来自同一个 `{capability-current-ref}@sha`；
- 没有覆盖其他 Baseline；
- 没有 Target/roadmap 内容；
- 可选章节只在实际适用时存在；
- Manifest、traceability 和 supersedes 已更新。

## 产出位置

优先遵守仓库已有目录。没有约定时：

```text
docs/architecture/as-is/
├── 00-baseline.md
├── 01-module-map.md
├── 02-capability-catalog.md
├── capabilities/
└── traceability.md
```

不要创建空目录或空能力。

## 输出契约

### Capability Baseline Package

- Baseline ID、Ref Profile 和 `{capability-current-ref}@full-sha`
- relevant module/version
- capability contract/guarantee/non-guarantee
- defaults/extension/limitations
- compatibility/migration（适用时）
- consumer evidence 或发现方式
- confidence 和 gaps

### Verify / Update Result

- `Verdict: IN_SYNC | DRIFT_FOUND | EVIDENCE_INCOMPLETE | BLOCKED`
- drift classification 和 evidence
- created/updated scope
- unresolved `CAPABILITY_BASELINE_GAP`

这些字段是与其他产物对齐的接口；本 Skill 不规定它们由哪个后续流程消费。
