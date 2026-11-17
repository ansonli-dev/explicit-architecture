---
name: review-codebase
description: Review implemented business-service code or a business-service code change against approved target decisions, a read-only central Architecture Context, a verified Service As-Is baseline, relevant Capability As-Is inputs, pending branch deltas, and external contracts. Use for feature-ref review, cumulative development-to-verification service review, or business codebase architecture audits that need prioritized implementation findings, governance candidates, and Service As-Is delta candidates; do not use to review shared SDK/library implementation, author a feature spec, curate central architecture documents, or directly update Canonical As-Is.
---

# 显式架构代码库审查

审查已经实现的代码，找出不合适的设计和实现，并给出修改方向。以实际依赖、运行路径和数据保障为证据，不把目录名、类名或当前代码形状当成架构正确性的证明。

## 审查立场

始终遵守：

1. 先恢复需求意图和运行路径，再判断单个类。
2. 代码是 Current Evidence，不是 Target Standard。
3. 区分业务语义、业务决策和技术保障；业务规则不要求每次都变成领域字段或领域预检查。
4. 优先指出根因和结构性改法，不建议依赖 `flush()`、调用顺序、重复预查询或框架偶然行为的局部补丁。
5. 既检查“放错位置”，也检查“本不该存在”的抽象。
6. 主动识别可提升为公共机制的能力缺口，但不把 bounded-context 领域概念错误共享。
7. 每条 finding 必须有代码证据、实际影响和可执行的修改方向；不要把个人偏好包装成缺陷。
8. 默认只审查和建议。用户没有明确要求修改时，不编辑生产代码、配置、schema 或 spec。

## 标准读取路由

开始前完整读取最小工作集：

- `../_standards/explicit-architecture/core-standard.md`：依赖、职责和设计判断；
- `references/scan-playbook.md`：扫描方法、证据门禁和报告格式。

根据实际审查范围再完整读取：

| Trigger | Read |
|---|---|
| 解析 Service/Capability 的实际 ref role | `../_standards/explicit-architecture/baseline-ref-profile.md` |
| 消费中央 Architecture Context 或输出治理候选 | `../_standards/explicit-architecture/architecture-context-contract.md` |
| 使用 Service Baseline、Pending Delta 或 As-Is Candidate | `../_standards/explicit-architecture/as-is-consumption-contract.md` |
| 使用公共能力 Current、版本、兼容或扩展点 | `../_standards/explicit-architecture/capability-as-is-consumption-contract.md` |
| 判断共享能力 fit/gap 或外部 contract/process readiness | `../_standards/explicit-architecture/discovery-gates.md` |
| 形成非平凡架构 finding 或执行反事实测试 | `../_standards/explicit-architecture/decision-framework.md` |
| 审查代码级产物、Mapper 或非标准抽象 | `../_standards/explicit-architecture/target-artifacts-and-mapping.md` |
| 审查 Brownfield 迁移、兼容、验证或过渡产物 | `../_standards/explicit-architecture/target-evolution-and-verification.md` |
| 结论依赖精确版本或技术能力 | `../_standards/explicit-architecture/technology-selection.md` |
| 一致性、并发、事务、异步副作用、重试、外部交互、长流程或技术派生状态 | `../_standards/explicit-architecture/mechanism-selection.md` |
| 非领域机制、shared/seedwork 扩展、复用或 Shared Kernel | `../_standards/explicit-architecture/capability-generalization.md` |
| finding 需要引用标准 ID | `../_standards/explicit-architecture/rule-index.md` |

未触发的标准不要预加载。触发后必须完整读取对应文件，引用标准 ID 前必须读取 `rule-index.md`。

## Review Mode

本 Skill 只审查可部署业务服务仓库。shared SDK/library 的内部实现质量不在范围内；其 Capability As-Is 只作为公共能力 contract、guarantee 和 extension 的设计输入。

选择：

- `FEATURE_REVIEW`：approved Spec + actual feature merge-base → feature SHA；
- `RELEASE_DELTA_REVIEW`：resolved last verified Baseline SHA → development/verification Candidate；
- `ARCHITECTURE_AUDIT`：Canonical Service As-Is + 当前业务服务实现，对选定 scope 做结构审查。

三个 mode 都可输出 Service As-Is Delta Candidate，但不修改 Canonical As-Is。

仓库规则与本 skill 不一致时，先解析权威顺序：

1. 用户对本次审查明确给出的 Target 决定；
2. 中央仓库和服务仓库声明为 canonical、且适用于当前 scope 的 Architecture、Standard 与 ADR；
3. 已接受的 feature spec 和 ADR；
4. 团队规范与模块说明；
5. 当前实现。

更高权威明确把仓库当作示例或 Current 时，不用当前实现覆盖 Target。实际读取和修改仓库仍必须遵守仓库操作规则。

中央 Architecture Context 始终只读。发现中央资料缺失、冲突、过期、不可发现或适用性不清时输出 `Architecture Governance Candidate`，不在代码审查中修订它。

## 确定审查范围

根据用户措辞选择最小充分范围：

- “审这个提交 / PR / diff”：使用实际 merge-base 审变更，同时读取足以理解运行路径的邻近代码；
- “审这个功能 / 模块”：追踪该功能的完整 vertical slice；
- “扫描代码库”：先建立模块和依赖概览，再按风险信号选择代表性运行路径，不宣称未扫描区域已经通过；
- 用户指定规则或问题：以该规则为主线，继续检查所有会影响其正确性的入口和保障。

若存在 Git diff，区分：

- 新增问题；
- 被本次变更暴露但已存在的问题；
- 本次变更必须一并处理的 Target 一致性问题；
- 可独立跟进的历史债务。

不要因为 finding 位于未修改行就自动忽略，也不要把无关历史债务塞入本次结论。

## 审查工作流

### 1. 建立 Current Evidence

读取仓库指令、canonical 架构文档、相关 ADR/spec、构建文件和模块边界。使用代码搜索确认：

- Canonical Service As-Is ID、Ref Profile、resolved verification ref/full SHA、environment 和 verification status；
- 中央 repository commit/version、index version、适用 document IDs、authority 和 applicability；
- resolved development/feature/verification candidate ref、实际 merge-base 和 Delta scope；
- Capability As-Is 的 Ref Profile、resolved current-ref/full SHA、module/version、contract、guarantee、non-guarantee、owner、state/lifecycle、extension、compatibility 和 consumers；
- 服务实际 resolved capability version、configuration/default override 和本地 adaptation（适用时）；
- 当前 feature 对已有能力是复用、配置、组合、扩展、替换还是平行实现；
- 适用 Architecture/Standards/ADR 是否得到遵守，Pattern 是否经过实际 forces 和技术栈裁决；
- 层间依赖和框架 import；
- 入口、Handler、Domain 行为、Port、Adapter 和 Mapper；
- 事务边界、保存路径和外部 I/O 顺序；
- schema、一致性约束、revision、Durable Intent、Retry、Idempotency；
- Domain Event 与 Integration Event 的转换和投递；
- 共享库已有能力及其消费者；
- 覆盖关键路径的测试。
- compiler/runtime/framework/driver/storage/messaging 的 effective version 与 production 配置。
- 外部集成使用的 contract/version、状态与业务流程证据；现有 client 只作为 Current Evidence。

把“已确认”“推断”“未找到”分开。未找到证据不等于不存在。

### 2. 追踪运行路径

至少追踪一条正常路径；有并发、异步或第三方交互时，同时追踪：

- 业务拒绝；
- 并发竞争；
- 超时或结果未知；
- 重复、乱序和重试；
- 事务提交前后；
- 补偿、对账和人工恢复。

从 driving adapter 进入 Application 和 Domain，再沿 Port 到 driven adapter；同时检查依赖是否反向渗透。

### 3. 分离语义、决策与保障

对关键规则分别确认：

- 哪个 bounded context 拥有业务含义；
- 哪个 Domain 行为或用例作出业务决定；
- 哪种机制提供最终正确性；
- 不变量在哪个时点成立；
- 机制是否适用于所有入口和并发路径。

允许 Infrastructure 从 Domain 状态派生条件约束键、版本、投递状态等技术字段，只要它们不进入 Domain、Command/Result 或业务协议。

### 4. 进行设计判断

同时检查：

- 是否把简单业务模型设计复杂；
- 是否把技术问题错误建模成业务状态；
- 是否为了 data-access/unit-of-work 限制破坏标准保存流程；
- 是否重复了权威持久化机制已经原子保障的检查；
- 是否出现无独立职责的 `Assembler`、`Router`、`Coordinator`、`Manager` 等；
- 层间表示转换是否统一由边界外侧的专属 Mapper 承担；
- 是否遗漏外部引用、外部状态到本地语义的映射或流程推进依据；
- 是否存在应该进入公共机制、却被堆在单服务内的可靠性能力；
- 是否在未取得 Capability As-Is 和服务采用事实时重复建设现有机制；
- 是否绕过可配置/可扩展的公共能力形成不兼容的平行实现；
- 是否把同形但不同语义的领域对象错误共享。
- 外部 Adapter、状态映射和恢复逻辑是否符合当前有效 contract/process，而非只符合现有 happy path；
- 实现是否只对当前入口或 happy path 成立，而不能通过 DR-08 反事实测试；
- 是否从框架限制倒推模型，而不是从 guarantee 和 failure model 选择机制；
- 是否存在能工作但在正确性、标准路径、恢复或演进上明显更弱的局部方案。
- 实现是否使用当前语言/框架/存储版本不支持、仍处于 preview，或未被 production 启用的能力；
- 是否因为记忆中的产品能力而错过当前版本的更优实现；
- 是否只验证底层产品，不验证 framework/data-access/driver/migration 的实际标准路径。

### 5. 验证 finding

报告前逐条确认：

1. 能定位到文件、符号、schema 或运行路径。
2. 能说明违反的目标规则或遗漏的设计责任。
3. 能描述现实影响，而不只是“看起来不优雅”。
4. 推荐方向保留标准流程，并给出自然职责所有者。
5. 已检查会推翻结论的反证，例如其他入口、约束、监听器或测试。
6. 严重度与影响相称。

证据不足时标记为 `Open question` 或降低置信度，不写成确定缺陷。

### 6. 生成 Service As-Is Delta Candidate

实现已经由 approved Spec/ADR 授权且有验证证据时，记录：

- affected As-Is fact/layer；
- old/new observed fact；
- source Spec/ADR；
- branch/commit/environment；
- verification status；
- suggested drift classification。

审查不把 Candidate 提升为事实，只输出可供 Service As-Is 维护流程核验的候选。

## 输出

先给 findings，按严重度排序；不要先写大段总体总结。

每条 finding 使用：

```text
[HIGH] 标题
Evidence: 文件:行号、符号或运行路径
Impact: 会导致的正确性、演进或维护问题
Standard: 被违反或遗漏的 `CTX-* / ASIS-* / CASIS-* / DISC-* / DR-* / MECH-* / GEN-* / TECH-*` 或目标规则
Recommendation: 目标职责归属和结构性修改方向
Scope: LOCAL | SHARED_CAPABILITY | TARGET_CONSISTENCY | FOLLOW_UP
Confidence: HIGH | MEDIUM | LOW
```

严重度：

- `CRITICAL`：可能造成数据丢失、跨边界污染、不可恢复副作用或系统性正确性失败；
- `HIGH`：核心依赖规则、业务不变量、并发、事务或外部契约存在实质问题；
- `MEDIUM`：职责错位、脆弱流程、重复机制或长期演进成本明显；
- `LOW`：局部一致性、命名或测试证据不足，不改变核心设计。

结尾给出：

- `Verdict`: `PASS | PASS WITH FINDINGS | CHANGES REQUESTED | BLOCKED`
- `Reviewed scope`: 实际检查过的路径；
- `As-Is delta candidates`: Service As-Is 事实变化候选；
- `Architecture governance candidates`: 中央上下文缺口；
- `Residual risks`: 因缺少运行、测试、生产 schema 或外部契约而无法确认的内容。

如果没有 finding，明确写“未发现需要修改的问题”，并列出已检查范围和剩余风险。不要为了显得有产出而制造意见。

## 边界

- 不代写或审核 feature spec；缺少 Target 时报告 `TARGET_GAP`，不在代码 review 中隐式代写。
- 不把建议机械拆成开发任务。
- 不要求为了“层次完整”创建空目录、Port、Event、Service 或 Mapper。
- 不给出与证据无关的全仓重构建议。
- 不创建、修改或提升 Canonical Service/Capability As-Is。
- 不创建、移动、整理或修改中央架构仓库。
- 不直接提交修复，除非用户明确要求实施。
