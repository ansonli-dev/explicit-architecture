---
name: review-spec
description: Review a developer-authored feature specification against requirements, a read-only central Architecture Context, a verified service As-Is baseline, relevant shared-capability owner baselines, pending deltas, approved decisions, and external contracts. Use when asked to approve, audit, critique, or provide revision guidance on a proposed feature spec, RFC, or ADR-backed design without rewriting it, reviewing implementation code, curating central architecture documents, or updating any As-Is.
---

# 显式架构 Spec 审查

审查 developer 提交的 spec 是否充分、准确、可演进，并给出定点修改建议。审查的对象是设计决策和缺失依据，不是文档排版，也不是重新代写一份 spec。

## 审查立场

始终遵守：

1. 先理解需求、端到端流程和系统边界，再审 Domain → Application → 外层的逐层设计。
2. 每层只拥有本层关注点；外层可以接收内层约束并派生技术结构，不能把技术细节反向写进内层。
3. 业务规则的语义、决策与最终保障可以分处不同位置；审查它们是否完整衔接，而不是要求重复实现。
4. 优先审查设计是否选择了最合适、最稳定、最容易长期维护的机制，不只检查“能否实现”。
5. 识别模型过度设计、无依据的新概念、遗漏的第三方流程依据和公共能力机会。
6. 当前代码库是可行性和演进证据，不自动等于 Target。
7. 默认只给修改建议。用户没有明确要求时，不改 spec、不写生产代码、不替 developer 完成整份设计。

## 标准读取路由

开始前完整读取最小工作集：

- `../_standards/explicit-architecture/core-standard.md`：依赖、职责和设计判断；
- `references/spec-rubric.md`：逐层审查问题、finding 门禁和审批格式。

根据待审 Spec 的实际内容再完整读取：

| Trigger | Read |
|---|---|
| 解析 Service/Capability 的实际 ref role | `../_standards/explicit-architecture/baseline-ref-profile.md` |
| 消费中央 Architecture Context 或输出治理候选 | `../_standards/explicit-architecture/architecture-context-contract.md` |
| 审查 Service Baseline、Pending Delta 或写入职责 | `../_standards/explicit-architecture/as-is-consumption-contract.md` |
| 审查公共能力 Current、版本、兼容或扩展点 | `../_standards/explicit-architecture/capability-as-is-consumption-contract.md` |
| 共享能力 fit/gap 或外部 contract/process readiness | `../_standards/explicit-architecture/discovery-gates.md` |
| 审查非平凡架构决定和反事实测试 | `../_standards/explicit-architecture/decision-framework.md` |
| 审查 Target 产物、Mapper 或非标准抽象 | `../_standards/explicit-architecture/target-artifacts-and-mapping.md` |
| Brownfield 迁移、兼容、验证或过渡产物 | `../_standards/explicit-architecture/target-evolution-and-verification.md` |
| 具体 stack、版本或候选实现 | `../_standards/explicit-architecture/technology-selection.md` |
| 一致性、并发、事务、异步副作用、重试、外部交互、长流程或技术派生状态 | `../_standards/explicit-architecture/mechanism-selection.md` |
| 非领域机制、shared/seedwork 扩展、复用或 Shared Kernel | `../_standards/explicit-architecture/capability-generalization.md` |
| finding 需要引用标准 ID | `../_standards/explicit-architecture/rule-index.md` |

未触发的标准不要预加载。触发后必须完整读取对应文件，引用标准 ID 前必须读取 `rule-index.md`。

按以下顺序解析权威：

1. 用户对本次审查明确给出的 Target 决定；
2. 中央仓库和服务仓库声明为 canonical、且适用于当前 scope 的 Architecture、Standard 与 ADR；
3. 已接受的 ADR、上游 spec 和外部合同；
4. 当前待审 spec；
5. 当前代码实现。

待审 spec 与更高权威冲突时报告 finding。当前代码与 Target 不同但 spec 已提供安全演进路径时，不因为“不像现有代码”否决 Target。

## 审查范围

优先读取用户指定的 spec。若 spec 分散在多个文件，先识别：

- 主文档；
- 需求与验收标准；
- ADR；
- API / event / schema 合同；
- migration 或 rollout 说明；
- 被引用但未随提交提供的材料。

只读取与验证设计依据和演进可行性有关的代码。不要把 spec review 变成无边界的代码库审计；实现本身需要专项扫描时，只列为独立后续事项。

Service As-Is 不存在或不可信时报告 `BASELINE_GAP`；Capability As-Is 不存在、与 resolved capability current-ref SHA 不一致或不可信时报告 `CAPABILITY_BASELINE_GAP`。不要在 review 中重建或更新任一 As-Is。

中央 Architecture Context 只读。索引、适用性、权威或内容存在缺口时，报告 Spec finding 或 `Architecture Governance Candidate`；不要在 review 中整理中央仓库。

## 审查工作流

### 1. 建立追踪骨架

从 spec 中提取：

- 中央 repository commit/version、index version 和实际引用的 document IDs；
- 每项中央文档的 type、status、authority、scope、applicability 和 exception；
- Service Ref Profile、As-Is Baseline ID、resolved `VERIFIED` SHA、environment 和 evidence timestamp；
- Capability Ref Profile、Baseline ID、resolved current-ref/full SHA、module/version、compatibility 和 evidence scope；
- development ref、Pending Develop Delta、feature merge-base；
- 需求、业务规则、验收标准和非功能约束；
- Design Forces、权威范围、有效时点和失败模型；
- 参与者、系统边界和端到端流程；
- 关键业务事实、状态和权威来源；
- Capability As-Is 的 contract/guarantee/extension/consumers，以及 Service As-Is 的 resolved version/configuration/adaptation；
- 每项能力的 fit/gap 决定；
- 适用的 External Integration Dossier、证据版本和 readiness；
- 计划新增、修改、迁移或删除的产物；
- Technology Capability Profile、版本证据和 fallback；
- 验证和上线证据。

为关键约束建立内存中的追踪链：

```text
Requirement
  → Domain meaning/decision
  → Application orchestration
  → Interface/Infrastructure/Integration guarantee
  → Verification
  → Current-to-Target action
```

任一必要环节缺失、冲突或重复保障，都形成候选 finding。

### 2. 审查 Context 与流程依据

确认 spec 在进入 Domain 前已经处理：

- 是否先从中央索引发现相关服务、owner、Architecture、Standards、Patterns、ADR 和资料入口；
- 是否固定实际读取的中央 commit/version，而非只写移动分支名；
- mandatory Standard 是否被遵守或有批准例外；
- Pattern 是否经过 forces/guarantee/技术栈裁决，而不是被机械套用；
- Landscape/Catalog 是否只用于发现，自然 owner 的 Current/contract 是否另有证据；
- Canonical Service As-Is 是否绑定 resolved verification ref 上的 immutable verified commit；
- Capability As-Is 是否绑定 resolved capability current-ref 的 immutable full SHA；
- 服务实际采用版本、配置和扩展是否有独立证据；
- 是否读取 development ref 相对 Baseline 的相关 Pending Delta；
- 是否错误把 development/feature 当前代码当成稳定 As-Is；
- 项目已有共享能力、公共模块、扩展点和实际消费者；
- 当前需求是复用、配置、组合、扩展、替换还是新增能力；
- 正常、拒绝、取消和补偿路径；
- 外部 contract/version/owner 与真实业务/运行流程；
- 第三方引用及其本地意义；
- 外部成功/失败/处理中/未知状态；
- 超时、重复、乱序、回调、对账和人工恢复；
- 本地单据是否以及如何被外部事实推进；
- 事实所有权、同步/异步边界和一致性期望。

这些内容如果会改变 Aggregate、状态机、契约或补偿，却仍不明确，标记 `BLOCKER`，不要允许用现有 client、实现假设或供应商印象填补。

### 3. 从内向外逐层审查

按顺序检查：

1. **Domain**：统一语言、Aggregate 边界、行为、不变量、业务状态、外部引用、Domain Event、写侧 Persistence 语义是否最小充分且不含技术细节。
2. **Application**：Command/Query/Result、Handler 编排、Port、原子性意图、I/O 顺序和长流程协调是否只表达用例责任。
3. **Interfaces**：Request/Response、协议校验、错误语义、Controller/Consumer/Listener 和边界 Mapper 是否只负责 driving adapter。
4. **Infrastructure**：当前 stack 对应的 persistence、constraint、派生字段、revision、Durable Intent、Retry、Idempotency、Cache/Search、transaction 和 Client Adapter 是否提供最终技术保障。
5. **Integration Contracts**：Domain Event 与 Integration Event 是否分离，schema、兼容、幂等、排序、投递、回放和外部状态映射是否明确。
6. **Verification**：Domain 行为、Handler 编排、Mapper、Adapter、并发、失败恢复和兼容迁移是否可验证。

只在对应层检查该层机制；例如条件约束键应出现在 Infrastructure 设计，不应成为 Domain 字段。

### 4. 审查跨层收敛

重点查找：

- 内层约束在外层无人保障；
- 同一规则被多层重复判断却仍不能解决并发；
- 技术字段、data-access/unit-of-work 顺序或第三方 DTO 泄漏到 Domain/Application；
- 为框架限制设计 `flush-before-save` 等非标准流程；
- Handler 出现业务判断或返回 Query Result；
- Command/Result、Domain、Persistence、Search、Integration 和第三方 DTO 的转换未统一使用边界外侧专属 Mapper；
- 无独立职责的 `Assembler`、`Router`、`Coordinator`、`Manager`、`Facade` 或泛化 `BaseMapper`；
- 把完整外部状态机复制成本地业务状态；
- 需要长流程状态，却误放入单 Aggregate 或普通 Handler；
- 公共可靠性机制被设计为单服务局部功能；
- 未盘点已有共享能力就提出新的局部或公共机制；
- 已有能力可配置、组合或扩展，却无依据平行建设；
- 相似但语义和治理不同的领域对象被放入 shared library。
- 外部 contract/process 仍未知，却已经固定本地状态机、字段映射、回调或补偿；
- 方案只适配当前案例，没有通过第二入口、并发、崩溃、重复、乱序或版本共存的反事实测试；
- 从框架 API 或现有类名直接选择实现，没有从 required guarantee 推导机制；
- 多个方案均可工作时，把代码量置于正确性、标准路径、恢复性和演进性之前。
- 架构标准写死某种语言语法、框架注解、数据库产品或版本能力；
- 未确认 effective version 就声称某能力可用或不可用；
- 底层产品支持，但 compiler/framework/data-access/driver/migration 标准路径实际不能安全使用；
- 使用开发或测试环境能力，而生产环境不支持。

### 5. 审查 Current → Target

Brownfield spec 必须说明：

- Current Evidence；
- Target 产物；
- `KEEP / MODIFY / ADD / MOVE / RENAME / REPLACE / DELETE`；
- API、event、schema、数据和部署兼容；
- Expand → Migrate → Contract 或等价安全顺序；
- 回填、双读/双写、切换、回滚和旧路径删除条件；
- 临时产物的 owner、删除条件和最迟删除点；
- 公共能力升级对现有消费者的影响。

不要让迁移妥协进入最终 Domain 模型，也不要要求 Target 围绕旧代码的偶然形状设计。

### 6. 验证 finding

每条 finding 必须：

1. 指向 spec 的标题、段落、图、表或明确缺失位置。
2. 关联需求、目标标准或跨层追踪断点。
3. 说明不修改会造成的设计或演进后果。
4. 给出 developer 可以落实的修改方向。
5. 保持在 spec 层，不以大段生产代码代替设计建议。

纯措辞偏好不报告为 finding。可以用一句短示例帮助定位修改，但不要重写整个章节。

## 输出

先给 findings，按严重度排序：

```text
[MAJOR] 标题
Location: spec 文件与章节
Issue: 缺失、冲突或不合适的设计
Why it matters: 对业务、架构或演进的影响
Suggested revision: 应补充、移动、删除或重新裁决什么
Trace: requirement / `CTX-*` / `ASIS-*` / `CASIS-*` / `DISC-*` / `DR-*` / `MECH-*` / `GEN-*` / `TECH-*` / target standard
```

严重度：

- `BLOCKER`：缺失信息会实质改变边界、状态机、所有权、一致性或外部合同，无法可靠批准；
- `MAJOR`：设计存在错误归属、关键遗漏、脆弱机制或不可执行的演进路径；
- `MINOR`：精度、追踪、图表选择或局部一致性需要改进，但不改变核心方案；
- `INSIGHT`：可提升为公共能力或需要单独架构裁决的机会，不自动阻塞当前 spec。

结尾给出：

- `Decision`: `APPROVE | APPROVE WITH COMMENTS | REQUEST CHANGES | BLOCKED`
- `Required revisions`: 阻塞批准的最小修改集合；
- `Non-blocking insights`: 不应强塞进当前 feature 的公共能力或后续治理建议；
- `Architecture governance candidates`: 中央上下文的缺失、冲突、过期或改进候选；
- `Review coverage`: 已审章节和查阅的代码/合同证据。

若没有 finding，明确批准并说明覆盖范围。不要为了展示严格而制造修改意见。

## 边界

- 不从需求生成新 spec，也不把 review 变成重写。
- 不以统一表格替代状态图、流程图或时序图；表达形式服从信息结构。
- 不因缺少不适用的层或产物而扣分；只要求 spec 明确说明为何不适用。
- 不要求开发者列出机械实现任务。
- 不把 Target 设计退化为“与当前代码一致”。
- 不创建、修订或提升 Canonical Service/Capability As-Is。
- 不创建、移动、整理或修改中央架构仓库。
- 不在一次 review 中无边界扩张公共能力实现范围。
- 批准后的 spec 输出明确的 Target、适用规则、验证和演进约束，供后续实现审查消费。
