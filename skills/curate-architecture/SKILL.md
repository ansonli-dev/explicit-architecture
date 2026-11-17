---
name: curate-architecture
description: Inventory, organize, govern, and identify specification-context gaps in a central architecture repository containing system landscape, cross-service architecture, project standards, reusable patterns, capability and integration catalogs, ADRs, and glossary. Use when asked to bootstrap or restructure the central architecture knowledge base, classify existing technical documents, build an applicability index, detect missing/conflicting/stale/duplicated guidance, or explicitly maintain approved architecture documents; do not use for ordinary feature development, Service or Capability As-Is synchronization, feature spec authoring/review, or code review.
---

# 整理中央 Architecture Context

维护中央架构治理仓库，使 Feature Skill 能稳定发现相关系统、边界、强制规范、候选方案、公共能力、集成依据和决定历史。按 Spec 决策需要判断充分性，不以文档数量或目录整齐度代替上下文质量。

## 标准读取路由

开始前完整读取最小工作集：

- `../_standards/explicit-architecture/architecture-context-contract.md`：中央仓库的内容、权威、只读消费和缺口规则；
- `references/repository-model.md`：为 Spec 提供上下文的内容域和默认组织；
- `references/curation-playbook.md`：盘点、分类、缺口分析、重组和验证方法。

只在触发条件成立时完整读取：

| Trigger | Read |
|---|---|
| 维护 Capability Catalog、核验能力 owner Current | `../_standards/explicit-architecture/capability-as-is-consumption-contract.md` |
| 审查或编写 Architecture/Standard 的架构质量 | `../_standards/explicit-architecture/core-standard.md`、`../_standards/explicit-architecture/decision-framework.md` |
| 可靠性、一致性、异步、重试、恢复或长流程 Pattern | `../_standards/explicit-architecture/mechanism-selection.md` |
| Shared Capability 或 Shared Kernel | `../_standards/explicit-architecture/capability-generalization.md` |
| 具体语言、框架、存储或消息实现 | `../_standards/explicit-architecture/technology-selection.md` |
| 输出精确 `CTX-*` finding | `../_standards/explicit-architecture/rule-index.md` |

未触发的标准不要预加载。触发后必须完整读取对应文件，再作结论。

## 工作边界

本 Skill：

- 盘点和分类中央仓库已有资料；
- 建立或修订 Architecture Context index；
- 识别对 Spec 有影响的缺失、冲突、过期、重复和适用性缺口；
- 按明确授权创建、拆分、移动、合并、弃用或归档中央文档；
- 可只读其他服务仓库、Service/Capability As-Is、合同和公共能力仓库，核验 owner、链接和覆盖。

本 Skill 不：

- 创建或修改 Service/Capability As-Is；
- 生成或审查 Feature Spec；
- 审查或修改生产代码；
- 将 Feature Governance Candidate 自动批准为中央规则；
- 将服务实现细节复制到中央仓库；
- 在只要求分析时移动、重命名、归档或改写文件。

普通服务开发只读中央仓库。只有用户明确要求维护中央仓库时才执行写入 mode。

## Mode

选择一个主要 mode：

### `INVENTORY`

只读盘点：

- 文件、目录、索引和链接；
- document type、authority、status、owner、scope 和 applicability；
- canonical、duplicate、mixed-authority 和 orphan 状态；
- Spec 内容域覆盖。

### `GAP_ANALYSIS`

只读判断中央上下文能否支持 Spec：

- 哪些需求类型无法发现适用资料；
- 哪些缺口会改变边界、所有权、合同、机制或验证；
- 哪些文档存在冲突、过期、不可验证或自然 owner 缺失；
- 哪些内容过细并复制了服务 Current。

### `BOOTSTRAP`

中央仓库尚无稳定组织时，使用 `assets/architecture-repository-template/` 建立最小结构、index 和适用文档。只创建真实需要的内容域，不创建空目录模拟完整。

### `MAINTAIN`

在明确写入授权下：

- 更新 index 和 metadata；
- 补充已批准内容；
- 拆分混合权威；
- 合并重复权威表达；
- 修复 owner/link/applicability；
- 标记 deprecated/superseded；
- 按批准方案移动或归档。

删除、覆盖、批量移动或改变 canonical authority 前必须确认精确目标和批准依据。优先保留历史并使用 supersedes/archive。

## 工作流

### 1. 固定 Repository 与权限

记录：

- central repository root、branch 和 immutable commit；
- repository instructions 和 canonical authority；
- mode、scope 和实际写入权限；
- index path/version；
- 允许修改和只读路径；
- 外部 evidence roots 及各自 commit/version。

共同上级目录只是可见范围，不是单一 Git 仓库。分别读取每个 repository 的规则和 ref。

### 2. 从 Index 开始发现

Index 存在时先读取并验证，再抽样或按风险打开正文。Index 缺失时：

1. 有界扫描 Markdown、diagram、schema、catalog 和 metadata；
2. 建立临时 inventory；
3. 报告 `UNINDEXED`；
4. 只有 `BOOTSTRAP/MAINTAIN` 才写入正式 index。

不得因为路径名叫 `standards` 就推断 authority。

### 3. 分类文档

将每份资料分类为：

- `LANDSCAPE`
- `ARCHITECTURE`
- `STANDARD`
- `PATTERN`
- `ADR`
- `CAPABILITY_CATALOG`
- `INTEGRATION_CATALOG`
- `GLOSSARY`
- `OUT_OF_SCOPE`

记录 status、authority、owner、scope、applicability、version、supersedes、related 和 natural owner。一个文档同时混写 mandatory rule、参考实现和 Current 事实时，标记 `MIXED_AUTHORITY`，不要通过修改标题掩盖问题。

### 4. 建立 Spec Context Coverage

按八个问题评估：

1. 能否发现相关服务、公共能力、owner、repository 和对应 As-Is？
2. 能否确定系统职责、事实所有权和依赖边界？
3. 能否理解影响本地模型的跨服务流程和失败恢复？
4. 能否发现适用 mandatory Standards 和批准例外？
5. 能否发现 Patterns、Shared Capabilities 及其选择边界？
6. 能否定位外部合同、流程 owner、状态 authority 和恢复资料？
7. 能否追踪 ADR、supersedes 和迁移约束？
8. 能否消除跨系统术语歧义？

对每个 gap 说明会影响哪类 Spec 决定。不会改变 Spec 的内容不因形式完整而强制补齐。

### 5. 验证自然所有者与适用性

抽查：

- Landscape 中的 service/repository/owner 链接；
- Standard 的 conformance、verification 和 exception process；
- Pattern 的 problem、forces、guarantee、non-guarantees、limitations 和 variants；
- Capability Catalog 指向的真实 contract/version owner；
- Integration Catalog 指向的 contract/process owner；
- ADR 的 status、decision 和 supersedes；
- `applies_to/applies_when` 是否能被 Feature Skill实际使用。

中央摘要与自然 owner 冲突时标记 `CONFLICTING`，不擅自选择或复制对方内容。

### 6. 形成 Gap 与变更方案

使用 `CTX-08` 分类。每条 finding 包含：

```text
[SEVERITY] Title
Type: MISSING | UNINDEXED | UNCLASSIFIED | AMBIGUOUS_APPLICABILITY |
      CONFLICTING | STALE | DUPLICATED | ORPHANED | BROKEN_LINK |
      UNVERIFIABLE | MIXED_AUTHORITY | OVERDETAILED
Evidence: file/section/index/link
Spec impact: boundary/model/ownership/contract/mechanism/technology/verification/evolution
Recommendation: KEEP | ADD | UPDATE | SPLIT | MERGE | MOVE | DEPRECATE | ARCHIVE | LINK
Owner: proposed governance owner
Rule: CTX-*
```

先给最小治理方案，再考虑目录重排。不要把所有历史文档强行迁移到模板结构。

### 7. 写入与验证

仅在 `BOOTSTRAP/MAINTAIN`：

1. 先修改正文或创建批准文档；
2. 同步更新 index、metadata、related 和 supersedes；
3. 修复受影响内部链接；
4. 保留自然 owner 链接，不复制其正文；
5. 验证无未替换 placeholder、重复 active authority 和断链；
6. 输出实际修改与未解决 gap。

不要修改服务或公共能力 Owner 仓库。需要 Current 更正时分别输出 Service As-Is 或 Capability As-Is owner follow-up。

## 推荐产出位置

优先遵守中央仓库现有 canonical 结构。没有约定时参考：

```text
architecture-context/
├── architecture-index.yaml
├── landscape/
├── architecture/
├── flows/
├── standards/
├── patterns/
├── capabilities/
├── integrations/
├── decisions/
└── glossary/
```

模板是默认起点，不是强制迁移目标。只创建有 owner、有用途、有适用范围的文件。

## 输出

先给 findings，再给总结：

- `Context readiness`: `SUFFICIENT | PARTIAL | INSUFFICIENT | BLOCKED`
- `Mode`
- `Reviewed scope`
- `Inventory summary`
- `Spec context coverage`
- `Required governance changes`
- `Applied changes`：只在写入 mode；
- `Unresolved conflicts`
- `External owner follow-ups`

若没有 finding，明确说明中央上下文足以支持哪些类型的 Spec，以及仍未覆盖的范围。不要为了显示整理成果而制造新目录或文档。
