# 中央 Architecture Context 契约

中央架构仓库维护跨项目的 Architecture、Standards、Patterns 和导航信息，为 Feature Spec 提供系统级设计上下文。它不是服务实现事实的中央副本，也不是 Feature 工作流的写入目标。

## 目录

- [CTX-01 分离治理上下文与服务现实](#ctx-01-分离治理上下文与服务现实)
- [CTX-02 建立可检索的适用性索引](#ctx-02-建立可检索的适用性索引)
- [CTX-03 提供最小系统图景](#ctx-03-提供最小系统图景)
- [CTX-04 区分 Architecture Standard Pattern 与 ADR](#ctx-04-区分-architecture-standard-pattern-与-adr)
- [CTX-05 链接自然所有者的事实](#ctx-05-链接自然所有者的事实)
- [CTX-06 Feature 流程只读](#ctx-06-feature-流程只读)
- [CTX-07 固定版本与适用范围](#ctx-07-固定版本与适用范围)
- [CTX-08 识别并治理上下文缺口](#ctx-08-识别并治理上下文缺口)
- [Spec 所需的最小内容域](#spec-所需的最小内容域)
- [Architecture Governance Candidate](#architecture-governance-candidate)

## CTX-01 分离治理上下文与服务现实

中央仓库只保存单服务无法自然拥有，或需要跨项目统一治理的内容：

- `LANDSCAPE`：服务目录、系统边界、职责和关系导航；
- `ARCHITECTURE`：系统级边界、依赖原则、事实所有权和跨服务协作约束；
- `STANDARD`：必须遵守的项目规范；
- `PATTERN`：根据场景和技术栈裁决的参考方案；
- `ADR`：已批准的架构决定及其演进关系；
- `CAPABILITY_CATALOG`：公共能力的发现索引与 owner 链接；
- `INTEGRATION_CATALOG`：外部系统、合同 owner 和流程资料的发现索引；
- `GLOSSARY`：跨系统统一语言和歧义消解。

以下内容不进入中央仓库：

- 服务内部 Domain/Application/Interfaces/Infrastructure 的完整 As-Is；
- 代码、schema、配置和运行事实的第二份副本；
- 单 Feature Spec、Plan 和开发任务；
- 未批准却伪装成规范的方案；
- 从服务仓库复制后无人维护的合同或实现说明。

服务 As-Is 是业务服务 Current 权威，Capability As-Is 是公共能力 Current 权威；中央 Architecture Context 是 Target 治理与发现输入。发生冲突时先分类 Current deviation、标准过期、适用范围错误或证据缺口，不让任何一方静默覆盖另一方。

## CTX-02 建立可检索的适用性索引

仓库必须提供入口索引。Agent 先读索引，再按需求选择文档，不递归加载整个仓库。

中央 repository root 从用户提供路径、仓库配置或已声明 workspace root 取得。不得通过无界扫描共同父目录、用户目录或任意相邻仓库猜测位置；无法解析时报告 `ARCHITECTURE_CONTEXT_GAP`。

每项至少包含：

| Field | Meaning |
|---|---|
| `id` | 稳定文档或规则标识 |
| `type` | `LANDSCAPE / ARCHITECTURE / STANDARD / PATTERN / ADR / CAPABILITY_CATALOG / INTEGRATION_CATALOG / GLOSSARY` |
| `title` | 人可读名称 |
| `path` | 仓库内稳定路径 |
| `status` | `DRAFT / ACTIVE / DEPRECATED / SUPERSEDED` |
| `authority` | `MANDATORY / ADVISORY / INFORMATIONAL` |
| `owner` | 内容和演进负责人 |
| `scope` | organization/system/service-group/boundary |
| `applies_to` | 适用服务、协议、能力或环境 |
| `applies_when` | 需求触发词或设计条件 |
| `version` | 规则或文档版本 |
| `last_reviewed` | 最近治理复核时间 |
| `supersedes` | 被替代文档 |
| `related` | ADR、标准、Pattern、能力和合同链接 |

索引负责发现，不重复正文。索引缺失或不可用时可以通过受限目录扫描恢复，但必须输出导航缺口。

## CTX-03 提供最小系统图景

为了让 Spec 在进入 Domain 设计前发现正确边界，中央仓库至少回答：

- 有哪些 system、service、bounded context 和 platform capability；
- 每个服务的稳定职责、owner、repository 和本地 As-Is 入口；
- 哪些事实由哪个系统权威拥有；
- 主要同步/异步依赖和公开 contract；
- 关键跨服务业务流程及正常、拒绝、未知、恢复和补偿路径；
- 外部系统、合同 owner、流程 owner 和资料入口；
- 共享能力的 owner、Capability As-Is/稳定 contract 入口和消费者发现方式；
- 已批准的系统级限制、例外和演进决定。

Landscape 是导航和治理视图，不证明某个 commit 或 release 已经实现这些内容。精确 Current 必须回到相关 Service As-Is、Capability As-Is、合同和运行证据。

## CTX-04 区分 Architecture Standard Pattern 与 ADR

不同类型具有不同权威：

| Type | Role in Spec | Required content |
|---|---|---|
| `ARCHITECTURE` | 定义系统边界、责任和长期 Target | problem、scope、boundaries、constraints、owners、exceptions |
| `STANDARD` | 强制约束，除非存在批准例外 | rules、applicability、rationale、conformance、verification、exception process |
| `PATTERN` | 候选方案，不得机械套用 | problem、forces、guarantee、applicability、limitations、variants、trade-offs、verification |
| `ADR` | 解释已批准决定及替代关系 | context、decision、consequences、status、supersedes |
| `LANDSCAPE` | 发现相关系统和资料 | services、roles、owners、relationships、links |

同一文档不得同时把 mandatory rule 和可选示例写成同等权威。Pattern 被组织强制采用时，将强制部分提升为 Standard，并保留 Pattern 的实现选择空间。

例如：

- 统一 HTTP Response 结构通常是 `STANDARD`；
- 多语言的业务语义、资源治理和 fallback 原则可以是 `ARCHITECTURE` 或 `STANDARD`；
- 特定框架、存储或翻译供应商的实现方式通常是 `PATTERN`；
- 已建设的国际化平台能力通过 `CAPABILITY_CATALOG` 链接真实 owner 和 contract。

## CTX-05 链接自然所有者的事实

中央仓库记录稳定摘要和发现链接，不成为以下事实的替代所有者：

- 服务实现和有效技术版本 → 服务 As-Is；
- API/event schema → provider 或 contract registry；
- 公共能力 contract/guarantee/version/compatibility → 能力所属仓库的 Capability As-Is；
- 外部系统合同和流程 → contract/process owner；
- 业务规则和流程 → 业务 owner；
- Feature Target → approved Spec/ADR。

必须标明链接的 owner、version/ref 和读取条件。若中央摘要与自然所有者冲突，标记 `CONFLICTING` 并停止依赖冲突部分，不擅自选一方。

## CTX-06 Feature 流程只读

服务功能开发、Current 维护和审查流程都只读中央仓库：

- Architecture、Standard 和 ADR 作为适用的 Target authority；
- Pattern 作为待裁决候选；
- Landscape/Catalog 只负责发现 owner 和自然事实入口；
- Feature Spec、实现或 As-Is 更新不得顺带整理中央仓库。

发现缺口、冲突、过期内容或公共治理机会时，输出 `Architecture Governance Candidate`。只有显式中央仓库维护流程可以写入。

## CTX-07 固定版本与适用范围

每次 Spec 或 Review 记录：

- central repository identity；
- immutable commit 或受治理 version；
- index version；
-读取时间；
- 实际选中的 document IDs；
- 每项的 status、authority、scope 和 applicability；
- 未读取、不可访问、冲突或过期的相关资料。

不能因为文档位于 `standards/` 就假定它适用。必须验证 status、scope、environment、protocol、service group 和已批准例外。

移动分支名可用于发现最新内容，但正式 Spec 必须固定实际读取的 commit/version。

## CTX-08 识别并治理上下文缺口

中央仓库治理至少识别：

- `MISSING`：关键内容域不存在；
- `UNINDEXED`：文档存在但无法通过索引发现；
- `UNCLASSIFIED`：类型或权威不明确；
- `AMBIGUOUS_APPLICABILITY`：无法判断何时适用；
- `CONFLICTING`：多份材料给出冲突约束或边界；
- `STALE`：已知系统或决定变化后未复核；
- `DUPLICATED`：同一规则有多个权威表达；
- `ORPHANED`：无 owner、无 consumer 或无相关入口；
- `BROKEN_LINK`：自然事实 owner 或相关资料不可达；
- `UNVERIFIABLE`：Standard/Pattern 未说明 conformance 或验证；
- `MIXED_AUTHORITY`：mandatory、advisory 和 example 混写；
- `OVERDETAILED`：复制了应由服务 As-Is、Capability As-Is 或合同 owner 维护的细节。

缺口结论必须关联潜在 Spec 影响：边界、模型、状态、所有权、协议、机制、技术选择、验证或演进。

## Spec 所需的最小内容域

中央仓库的充分性按以下问题评估，而不是按文档数量：

1. **System discovery**：能否找到相关服务、公共能力、owner、repository 和对应 As-Is？
2. **Boundary and authority**：能否确定系统职责、事实所有权和允许的依赖？
3. **Cross-service process**：能否理解影响本地模型的端到端流程和失败路径？
4. **Mandatory conformance**：能否发现当前需求必须遵守的 Standards 和批准例外？
5. **Reusable solutions**：能否发现相关 Patterns、Shared Capabilities 及其选择边界？
6. **Integration basis**：能否定位外部合同、流程、状态 authority 和恢复资料？
7. **Decision history**：能否找到仍有效的 ADR、supersedes 和迁移约束？
8. **Language**：能否消除跨系统业务和技术术语歧义？

某内容不会改变 Spec 的发现、边界、责任、机制、合同、验证或演进时，不因“文档完整”而强制创建。

## Architecture Governance Candidate

Feature Skill 只输出候选：

| Field | Content |
|---|---|
| Candidate type | `ARCHITECTURE_CHANGE / STANDARD_CHANGE / PATTERN_CHANGE / LANDSCAPE_GAP / ADR_NEEDED` |
| Trigger | requirement、Spec finding、code finding 或 As-Is deviation |
| Current authority | 现有 document ID/version |
| Gap | 缺失、冲突、过期或不适用原因 |
| Proposed direction | 建议补充、拆分、替代或澄清的方向 |
| Scope | 影响的服务、能力、协议和环境 |
| Evidence | As-Is、contract、Spec、code 或 runtime evidence |
| Compatibility | 对现有消费者和已批准设计的影响 |
| Urgency | `BLOCKING / REQUIRED_BEFORE_DELIVERY / FOLLOW_UP` |

Candidate 不是批准决定，也不能被 Feature Skill 直接写回中央仓库。
