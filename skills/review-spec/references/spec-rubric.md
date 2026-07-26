# Spec 审查量表

本量表用于逐层发现缺失、冲突和过度设计。不是要求每份 spec 机械包含所有章节；不适用内容只需有可信理由。

## 目录

- [Context Gate](#context-gate)
- [Decision Quality Gate](#decision-quality-gate)
- [Technology Fit Gate](#technology-fit-gate)
- [Domain Gate](#domain-gate)
- [Application Gate](#application-gate)
- [Interfaces Gate](#interfaces-gate)
- [Infrastructure Gate](#infrastructure-gate)
- [Integration Gate](#integration-gate)
- [Capability Gate](#capability-gate)
- [Evolution Gate](#evolution-gate)
- [Verification Gate](#verification-gate)
- [Finding Gate](#finding-gate)

## Context Gate

- Spec 是否记录中央 Architecture Context 的 immutable commit/version、index version 和选中文档 ID？
- 是否根据 requirement、service、protocol、capability 和 environment 检查适用性，而不是只读熟悉文档？
- `ARCHITECTURE / STANDARD / PATTERN / ADR / LANDSCAPE` 的权威是否区分？
- mandatory Standard 是否得到遵守或有批准例外？
- Pattern 是否经过 forces、guarantee、当前技术栈和演进裁决？
- Landscape/Catalog 是否只用于发现，并继续读取 Service/Capability As-Is 和自然 owner contract？
- 中央上下文缺失、冲突、过期或不可访问时，是否形成 `ARCHITECTURE_CONTEXT_GAP` 或 Governance Candidate？
- Spec 是否记录 Ref Profile，并绑定 immutable `VERIFIED` Baseline SHA，而不是只写移动分支名？
- 是否读取相关 Pending Develop Delta 和实际 feature merge-base？
- 是否把 development/feature ref 的未验证变化误写成 Canonical As-Is？
- 需求、规则、验收和非功能约束是否可追踪？
- 是否取得与当前需求相关且绑定 resolved capability current-ref/full SHA 的 Capability As-Is，而不是只列计划新增能力？
- 是否分别记录能力 Owner 的 contract/guarantee/compatibility 与服务实际 resolved version/configuration/extension？
- 是否记录已有能力的 owner、consumers、限制和 fit/gap？
- 主路径、拒绝、取消、补偿和恢复是否清楚？
- 参与者、系统边界、事实所有权和一致性期望是否明确？
- 外部系统的当前有效 contract、版本、环境差异和业务/运行流程是否有证据？
- 第三方引用、外部状态、未知结果、回调和对账是否提供建模依据？
- 是否仍有会改变 Aggregate、状态机、所有权或合同的未决问题？

## Decision Quality Gate

- 是否从业务结果和 design force 开始，而非从类、表或框架 API 开始？
- authority、scope 和 effective point 是否覆盖真实不变量？
- failure model 是否区分 prevent/detect/recover/tolerate/exclude？
- semantic、decision、mechanism 和 operational owner 是否分开？
- 所选机制是否明确引用它能提供的 guarantee 和不能解决的 residual risk？
- 是否保持标准路径？
- 是否执行第二入口、并发、崩溃、重复、乱序、未知结果和版本共存等适用反事实测试？
- 多个可行方案是否按正确性、单一权威、层自治、标准路径、恢复和演进顺序裁决？
- 关键决定是否有 `CTX-* / ASIS-* / CASIS-* / DISC-* / DR-* / MECH-* / GEN-*` 追踪和验证证据？

## Technology Fit Gate

- Spec 是否先表达技术中立的 architecture intent 和 mechanism family？
- 是否从仓库、部署和运行配置确认 effective language/framework/storage/messaging 版本？
- 版本敏感能力是否有当前精确版本的一手证据？
- 是否检查 compiler、framework、serializer、data-access/unit-of-work、driver 和 migration 限制？
- 是否检查 production 与其他环境能力交集？
- 是否根据语义形状选择语言表示，而不是固定 enum、sealed hierarchy、record 或其他语法？
- 是否根据当前存储版本的真实约束语义选择 index、generated/computed representation、atomic mutation 或其他实现？
- 是否记录 fallback、限制和重新评估条件？
- 技术细节是否只出现在拥有它的外层？

## Domain Gate

- 模型是否使用业务语言，而不是表名或供应商状态码？
- Aggregate 是否由行为和一致性划分？
- 每个 Entity/Value Object/Domain Service/Event 是否有需求依据？
- 业务判断是否位于 Aggregate 或纯 Domain Service？
- 全局规则是否表达语义并正确移交外层保障？
- 是否混入持久化框架/查询语言、锁、技术派生字段、重试、缓存、协议或 Mapper？
- 外部状态是否只映射为真正影响本地权利义务的事实？
- 是否可删除无行为模型或无意义抽象？

## Application Gate

- Command 与 Query 及各自 Result 是否分离？
- Handler 是否只加载、调用 Domain、保存和返回本次用例结果？
- 是否为 Command 返回值额外走 Query 或重新 I/O？
- 写侧 Aggregate Persistence 是否留在 Domain 语义，Client/Cache/Search/Read Port 是否位于 Application？
- 原子性意图和外部 I/O 顺序是否明确？
- 需要持久长流程时是否真正使用 Process Manager / Saga，而非普通 Handler 同义包装？
- Command ↔ Domain input/Result 是否由专属 Mapper 处理？

## Interfaces Gate

- Controller/Consumer/Listener 是否只负责协议、映射和分发？
- Request/Response 与 Application Result 是否分离？
- 语法校验、协议错误和业务拒绝是否区分？
- 外部 event/webhook 是否经 Mapper 转为本地 Command/input？
- 是否错误增加业务 Router、Assembler 或 Controller 内业务判断？

## Infrastructure Gate

- Persistence Model 是否独立于 Domain，并承载必要技术派生字段？
- 并发、唯一性、版本和事务边界是否有最终保障？
- 是否保持标准保存路径，避免 flush/顺序技巧？
- 外部 I/O 是否在本地资源事务之外？
- Persistence ↔ Domain、projection、search、client、cache 是否由专属 Mapper 转换？
- Durable Intent、Retry、Idempotency、Cache/Search 技术副作用是否位于正确机制？
- 技术异常是否转换为稳定的 Domain/Application/Protocol 语义？

## Integration Gate

- 外部 contract/process evidence 和 readiness 是否足以支持当前设计？
- 是否区分现有 client 行为与外部系统正式合同？
- Domain Event 与 Integration Event 是否分离？
- schema owner、版本和兼容策略是否明确？
- 投递语义、幂等键、顺序、重复、重放和 dead-letter 是否明确？
- 同服务业务反应和纯技术 listener 是否正确区分？
- 超时、未知、补偿、对账和人工恢复是否可执行？
- 是否避免在 Handler 中直接发布消息？

## Capability Gate

- 是否先扫描 shared module/service/platform capability 及其真实 consumers？
- 当前决定是 reuse/configure/compose/extend/wrap/replace/new/local 中的哪一种？
- 已有能力的 fit/gap 是否有证据，是否错误平行建设？
- 新 retry/durable-intent/idempotency/scheduler/dispatcher 是否暴露公共机制缺口？
- 当前服务是否只需提供业务 Handler、Mapper、Payload、Policy 或配置？
- 公共核心能否去除 bounded-context 词汇？
- 是否明确建议 owner、稳定扩展点和现有消费者影响？
- 是否把同形但不同语义的 Domain 类型错误共享？

`INSIGHT` 不自动阻塞。只有缺少公共机制会让当前方案重复、脆弱或无法满足约束时，才提升为 `MAJOR`。

## Evolution Gate

- Current 是否以 Canonical Service/Capability As-Is + Pending Delta 表达？
- Current Evidence 与 Target 是否分开？
- 产物的新增、修改、移动、替换和删除是否明确？
- API、event、schema、数据和部署兼容是否处理？
- Expand、回填、切换、Contract 和回滚顺序是否安全？
- 过渡产物是否有 owner、删除条件和最迟删除点？
- 是否说明 shared capability 升级对其他消费者的影响？
- 是否有 characterization、跨版本和迁移验证？

## Verification Gate

- Domain 行为和不变量有纯单元验证？
- Handler 编排和 Port 交互可验证？
- Mapper 的有损、未知值、派生字段和错误语义可验证？
- 持久化约束、事务和并发是否使用实际 adapter、存储产品和有效版本验证？
- 协议、schema 和兼容性有契约验证？
- 重复、乱序、超时、重试、补偿和恢复有故障路径验证？
- 每条验收标准能追踪到设计和证据？

## Finding Gate

报告前确认：

1. finding 能定位到章节或明确缺失位置；
2. 有目标标准或需求依据；
3. 不修改会产生具体后果；
4. 建议说明补充、移动、删除或重新裁决的方向；
5. 严重度与影响一致；
6. 不是个人措辞或版式偏好；
7. 没有因“形式完整”要求不必要产物。
8. 中央上下文问题没有被错误写成 Service/Capability As-Is 或 Feature 实现问题。

如果信息只能从代码、合同或上游需求确认，先读取证据；仍无法确认时写成问题或 `BLOCKER`，不要替 developer 发明答案。
