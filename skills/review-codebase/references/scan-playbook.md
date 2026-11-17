# 代码库扫描方法

本方法用于获得足够证据并控制扫描范围。不要只按包名匹配，也不要把搜索命中数量当成结论。

## 目录

- [建立地图](#建立地图)
- [读取中央 Architecture Context](#读取中央-architecture-context)
- [建立共享能力基线](#建立共享能力基线)
- [恢复外部集成依据](#恢复外部集成依据)
- [发现有效技术栈](#发现有效技术栈)
- [风险驱动搜索](#风险驱动搜索)
- [Vertical Slice 追踪](#vertical-slice-追踪)
- [反事实探针](#反事实探针)
- [反证检查](#反证检查)
- [报告质量](#报告质量)

## 建立地图

先找：

1. 仓库指令和 canonical 规范；
2. 中央 Architecture Context 的 index、immutable commit/version 和适用文档；
3. Canonical Service As-Is、Ref Profile 和 resolved verified Baseline SHA；
4. resolved development/feature/verification candidate SHA 与 merge-base；
5. 模块、构建边界和共享库；
6. 待审 diff 或 feature 入口；
7. schema、migration、配置和测试；
8. 已接受 spec/ADR。

优先使用 `rg --files` 和 `rg`。只读检查可以扩展到邻近模块、共享能力及消费者，但在报告中准确写出实际覆盖范围。

分支读取使用 immutable ref。不要为了 review checkout、merge、rebase 或 reset 用户当前分支。

## 读取中央 Architecture Context

中央仓库只读：

1. 先读 index，根据被审服务、协议、能力、integration 和 environment 选择文档；
2. 固定 document ID、type、status、authority、scope 和 applicability；
3. 用 Architecture/Standard/ADR 判断 Target，用 Pattern 发现候选，用 Landscape/Catalog 找自然 owner；
4. 检查批准例外和 supersedes；
5. 精确业务服务事实回到 Service As-Is、合同、代码和运行证据；公共能力 contract 回到 Capability As-Is。

中央内容缺失、冲突、过期或与自然 owner 不一致时输出 Architecture Governance Candidate。不要在代码 review 中修改中央仓库，也不要把中央问题错误归因于实现。

## 建立共享能力基线

在判断服务内实现是否重复或应公共化前：

- 从 Capability As-Is 找到公共 module、shared service、internal SDK/starter 和 platform extension；
- 读取其 Ref Profile、resolved capability current-ref/full SHA、module/version、public contract、guarantee、configuration、durable state、兼容和运行组件；
- 从目标 Service As-Is 确认 resolved version、配置、本地扩展和调用路径；
- 搜索实际 consumers、测试、部署和兼容约束；
- 记录 `CONFIRMED / INFERRED / UNKNOWN`；
- 将当前实现分类为 reuse/configure/compose/extend/wrap/replace/new/local。

不要只搜索与当前类同名的能力。按 guarantee、state lifecycle、failure recovery 和 operational owner 匹配。

## 恢复外部集成依据

代码涉及外部系统时，先定位：

- 正式 contract/schema/API/event 文档及有效版本；
- accepted/completed/unknown 等业务流程和事实 authority；
- auth、idempotency、ordering、callback/query、timeout、rate limit 和 error semantics；
- sandbox 与 production 差异；
- contract test、schema registry、ADR 和 change policy。

现有 client、DTO 和测试是 Current Evidence；没有正式依据时不能断言其为完整 Target contract。缺失信息会改变本地状态机、引用、恢复或补偿时，将其报告为阻塞证据缺口。

## 发现有效技术栈

在使用任何技术特定搜索模式或建议前：

- 读取 build manifest、lockfile 和 toolchain/compiler target；
- 读取 runtime/container image 和 CI matrix；
- 读取 framework、data-access/unit-of-work、driver、migration 和 serializer 的 resolved version；
- 读取 storage/messaging/cache/search 的部署版本、edition、engine、extension 和关键配置；
- 比较 local/test/staging/production；
- 为当前 finding 依赖的能力建立 Technology Capability Profile。

版本敏感能力必须使用当前精确版本的官方 reference/spec、本地 dependency source、server capability query 或最小实验核验。未确认时降低置信度或报告 `Open question`。

## 风险驱动搜索

根据已确认技术栈调整模式。以下只是某类仓库的搜索示意，不是默认技术要求：

```text
@Entity | @Transactional | flush(
KafkaTemplate | publish | dispatch
Repository | Persistence | Adapter | Mapper
from( | of( | Converter | Assembler
Coordinator | Orchestrator | Router | Manager
retry | outbox | idempot | dead.letter
@Version | unique | index | constraint
RestClient | WebClient | Feign | HTTP
```

搜索结果只用于发现候选路径。必须打开定义和调用方，确认依赖、事务、数据流和行为。

## Vertical Slice 追踪

对每个高风险功能记录：

```text
External input
  → Request/Event Mapper
  → Command/Query
  → Handler
  → Domain behavior
  → Persistence/Outbound Port
  → Adapter/constraint/reliable mechanism
  → Result/Response/Integration Event
```

继续检查：

- 该路径使用或绕过了哪些共享能力；
- 外部行为是否能追溯到有效 contract/process；
- 所有同类入口是否经过同一规则和保障；
- 保存成功后 Domain Event 如何转移、清除和发布；
- 持久化冲突或异常如何翻译为稳定业务/应用错误；
- Query 是否绕过 Domain 并使用投影；
- cache/search 等技术副作用是否由 Infrastructure 管理；
- 测试是否覆盖实际选定存储的原子性和并发语义，而不只是 mock。

## 反证检查

提出 finding 前搜索可能推翻它的证据：

- 约束是否在 migration 中而非 annotation 中；
- 特殊行为是否由共享 library 自动提供；
- 事务是否由更外层的明确例外承担；
- 另一个 Mapper 是否已经是权威转换路径；
- 看似未处理的失败是否由 consumer framework 或统一 retry engine 处理；
- 当前代码是否是带删除条件的过渡实现；
- canonical ADR 是否批准了例外。

不能确认时写清假设和置信度。不要要求用户根据猜测改代码。

## 反事实探针

不要只复现当前测试。对候选设计按适用性检查：

- 新增另一个 REST、Consumer、Job 或批处理入口；
- 两个 writer 同时基于旧状态决定；
- 进程在 save、publish、execute、ack 任一边界前后崩溃；
- 消息或回调重复、乱序和延迟；
- 外部 timeout 但实际成功；
- 新旧 schema 或服务版本滚动共存；
- 从单实例扩展为多实例；
- 更换 data-access/driver 后写入顺序变化；
- shared capability 新增另一类消费者。

反事实揭示的问题必须回到 authority、scope、failure model 和 mechanism owner 分析，不能只添加针对测试的 if/else。

## 报告质量

好的修改建议说明目标形状，而不是只说“重构”：

- 把哪项职责移到哪个自然所有者；
- 删除或合并哪个无效抽象；
- 哪个约束应由 Domain 表达语义、由权威持久化机制提供最终保障；
- 哪个局部机制应升级为 shared capability；
- 哪个现有 shared capability 应被复用、配置、组合或扩展；
- 哪个外部对接判断需要先补 contract/process evidence；
- 哪条非标准流程应恢复为普通 save/commit 路径；
- 需要怎样的兼容和迁移顺序；
- 用什么测试或运行证据证明修改成立。

不要在 review 中直接展开完整实现代码。只有用户要求修复时，才从审查切换为实现任务。

业务服务实现事实符合 approved Spec/ADR 且已有验证证据时，另列 Service As-Is Delta Candidate；不要直接编辑 As-Is。

中央上下文存在缺口时另列 Architecture Governance Candidate；不要直接编辑中央仓库。
