# 技术实现选择标准

架构标准只定义语义、责任和机制族；具体语言构造、框架扩展点、数据库约束、消息能力和迁移方式必须根据相关 Canonical Current 中已验证的技术栈、精确版本和运行环境选择。本文件规定 Target 如何消费技术事实并选择实现；业务服务技术事实和公共能力事实分别回到其自然 Owner Current。

## 目录

- [技术中立原则](#技术中立原则)
- [TECH-01 先确定架构意图](#tech-01-先确定架构意图)
- [TECH-02 消费有效技术栈](#tech-02-消费有效技术栈)
- [TECH-03 使用 Technology Capability Profile](#tech-03-使用-technology-capability-profile)
- [TECH-04 核验版本能力](#tech-04-核验版本能力)
- [TECH-05 核验集成层限制](#tech-05-核验集成层限制)
- [TECH-06 核验环境交集](#tech-06-核验环境交集)
- [TECH-07 选择最优实现](#tech-07-选择最优实现)
- [TECH-08 记录证据与失效条件](#tech-08-记录证据与失效条件)
- [语言表示选择](#语言表示选择)
- [持久化实现选择](#持久化实现选择)
- [消息与异步实现选择](#消息与异步实现选择)
- [技术变更与架构边界](#技术变更与架构边界)
- [Technology Decision Record](#technology-decision-record)

## 技术中立原则

Target 生成和审查 **MUST NOT**：

- 把某种语言特性写成 Domain 模型的必选表达；
- 假设仓库使用特定语言、框架、data-access 技术、数据库、消息系统、缓存、搜索或迁移工具；
- 假设使用最新版本；
- 根据产品名称记忆能力，而不核对精确版本、edition、engine、extension 和配置；
- 因为当前技术不直接支持某机制，就把技术妥协写入 Domain；
- 在版本不明确时伪造可用性结论。

Target 生成和审查 **MUST**：

- 先完成适用的 `DISC-*`，确认现有共享能力与外部 contract/process；
- 先完成 `DR-*` 和 `MECH-*` 的技术无关判断；
- 从 Service As-Is 消费目标服务有效技术栈，从 Capability As-Is 消费公共能力发布与兼容范围；
- Profile 缺失、过期或与绑定环境冲突时报告 Baseline Gap；
- 核验候选实现是否被实际版本支持；
- 检查语言/框架/driver/data-access/存储之间的组合限制；
- 在可用实现中选择最符合 guarantee、标准路径、维护和演进的方案。

## TECH-01 先确定架构意图

在检查具体 API 或语法前，先写：

- business meaning；
- required guarantee；
- authority、scope 和 effective point；
- failure model；
- mechanism family；
- layer owner。

示意：

```text
Architecture intent:
Represent a closed set of business states with explicit legal transitions.

Implementation candidates:
enum / tagged union / sealed hierarchy / closed class hierarchy / equivalent construct
```

候选实现不是同义替换。必须根据状态是否携带不同数据、是否有多态行为、穷尽性检查、序列化和团队语言能力选择。

## TECH-02 消费有效技术栈

从相关 Service/Capability Current 取得当前 scope 的：

- language、compiler 和 runtime；
- framework、data-access、serializer、client 和 driver；
- database、engine、extension、messaging、cache 和 search；
- migration、deployment 和 observability tooling；
- local、test、staging、production 的有效版本和配置差异。

每项事实必须有 environment、effective version、evidence 和 confidence。不从目录名、未解析 manifest 或产品名称猜测，不默认选择声明中的最高版本。

缺失事实会改变机制、数据模型、兼容或交付时，具体实现决定保持 `BLOCKED`，并明确缺失的是 Service Baseline 还是 Capability Baseline。代码审查可以报告 observed mismatch，但不能自行改写任一 Canonical As-Is。

## TECH-03 使用 Technology Capability Profile

实现选择至少读取：

| Concern | Product / language / framework | Effective version/config | Evidence | Relevant capability | Limitation | Confidence |
|---|---|---|---|---|---|---|
| {{CONCERN}} | {{TECH}} | {{VERSION}} | {{SOURCE}} | {{CAPABILITY}} | {{LIMIT}} | `CONFIRMED / INFERRED / UNKNOWN` |

Profile 只需覆盖当前决定实际依赖的能力，不要求 Target 文档复制整个依赖树。Spec 摘录相关条目并引用 Baseline。

`UNKNOWN` 如果会改变机制选择、数据模型、兼容或交付，应阻塞具体实现决定；可以继续完成技术无关 Target intent。

## TECH-04 核验版本能力

当实现依赖版本敏感能力时，**MUST** 使用以下证据顺序：

1. 仓库已锁定的 dependency/runtime metadata；
2. 当前精确版本的官方 reference/specification；
3. 本地 dependency source、driver metadata 或服务器 capability query；
4. 受控最小实验；
5. 二手资料只用于发现线索，不能作为最终依据。

核验：

- 引入版本和移除/弃用版本；
- preview/incubator/experimental/stable 状态；
- edition、engine 或 extension 限制；
- configuration/feature flag；
- 语法支持与真实语义；
- client/driver 是否暴露；
- migration/online operation 限制；
- 当前环境是否允许。

不要把“某产品支持”简化为跨版本永久事实。

## TECH-05 核验集成层限制

底层能力存在，不代表当前标准路径可以正确使用。继续检查：

- compiler target 是否允许语言特性；
- framework 是否支持该构造；
- serializer/schema tool 是否保持语义；
- data-access/unit-of-work 的 create/update/delete ordering；
- driver 对数据类型、返回值、错误码和 transaction 的支持；
- migration tool 能否安全创建、回填和回滚；
- proxy/code generation/reflection 对类型的要求；
- test tooling 是否能验证真实行为；
- observability 是否能暴露失败。

如果集成层限制使实现依赖特殊顺序，回到 `DR-07`。优先选择可保持标准路径的另一种外层表示或实现。

## TECH-06 核验环境交集

多环境版本不一致时，明确策略：

- 使用生产最低共同能力；
- 分阶段升级后再启用新机制；
- 使用 feature detection/compatibility mode；
- 按环境提供不同 Adapter，但保持相同内层 contract；
- 明确某环境不被支持。

不得让 local/test 使用的能力在 production 不存在。不得因为测试工具落后而永久降低 Target；可以设计测试替代或工具升级。

## TECH-07 选择最优实现

在被当前 stack 支持的候选中，按顺序比较：

1. 是否完整实现 required guarantee；
2. 是否保持层边界；
3. 是否通过标准路径；
4. 是否在并发和失败模型下成立；
5. 是否可观测、可恢复；
6. 是否支持当前数据和滚动部署演进；
7. 是否使用稳定而非 preview/experimental 能力；
8. 是否符合团队已有一致约定；
9. 运维与认知成本；
10. 局部代码量。

版本较新不自动更优；更熟悉也不自动更优。

记录至少一个 fallback，当：

- capability 仍是 preview/experimental；
- production version 尚未统一；
- managed service 限制不确定；
- migration 风险较高；
- 当前实现需要基础设施升级。

## TECH-08 记录证据与失效条件

每个版本敏感决定记录：

- 选择依据；
- 精确版本和配置；
- 官方或本地证据；
- 已验证的语义；
- 限制；
- fallback；
- 何时需要重新评估；
- 升级/降级影响；
- 测试方式。

依赖“当前 unit-of-work 排序”“当前 broker 默认”“当前 runtime flag”等可变行为时，必须有自动验证或拒绝该方案。

## 语言表示选择

先判断语义形状：

| Semantic shape | 需要的语言能力 |
|---|---|
| 简单闭合标签，无变体数据 | enum-like closed constants |
| 闭合变体，各自携带不同数据 | tagged/discriminated union 或 sealed hierarchy |
| 行为随变体变化 | 可穷尽多态表示 |
| 开放扩展 | interface/protocol/open hierarchy |
| 值语义和不可变 | immutable value representation |

再基于语言与版本选择构造。不要规定所有项目使用 enum、sealed class、record、data class、union type 或任何特定语法。

即使语言支持某特性，也检查：

- 当前 compiler target；
- serialization/persistence support；
- proxy/reflection restrictions；
- exhaustive matching；
- binary/source compatibility；
- 团队编码规范。

## 持久化实现选择

先从 `MECH-*` 确认需要：

- declarative invariant；
- atomic guarded mutation；
- conflict detection；
- serialization；
- local transaction；
- durable intent；
- derived representation。

再核验当前存储产品和版本的：

- unique/check/exclusion/foreign-key 等约束语义；
- conditional/filtered/partial/function/expression index；
- generated/computed/virtual/stored columns；
- deferred constraint；
- null semantics；
- transaction isolation 和 lock；
- atomic update/returning/compare-and-set；
- online DDL；
- trigger/procedure 限制；
- partition/shard/global index；
- error code stability。

具体能力名称可能相似但语义不同。必须同时检查 data-access/driver 和 migration 路径。

如果目标 predicate 无法被当前存储直接表达：

1. 判断能否在 Persistence Model 派生约束表示；
2. 判断能否使用原子条件变更或冲突检测；
3. 判断是否需要更强协调机制；
4. 判断升级/扩展存储是否合理；
5. 明确剩余风险和对账；
6. 不把技术状态推入 Domain。

## 消息与异步实现选择

先确认 `MECH-06` 至 `MECH-10` 中需要的 guarantee，再核验：

- producer transaction/atomicity；
- delivery semantics；
- partition/key ordering；
- duplicate behavior；
- consumer acknowledgement；
- retry/dead-letter；
- scheduled delivery；
- schema evolution；
- replay/retention；
- idempotency storage；
- tracing/header propagation。

不要把 broker 的营销语义直接当成端到端业务 guarantee。Producer、broker、consumer、handler 和 effect store 的组合才决定结果。

## 技术变更与架构边界

当前技术栈是实现约束，不一定是不可改变的业务约束。

若当前版本无法优雅实现 required guarantee，比较：

- 在现有 stack 内采用次优但正确的标准路径；
- 升级版本；
- 启用 extension/feature；
- 更换 Adapter 或局部技术；
- 调整非功能要求；
- 接受并显式管理 residual risk。

升级或替换超出当前 feature 权限时，记录 Target、fallback 和所需 ADR，不擅自扩大实现范围。

## Technology Decision Record

| Field | Content |
|---|---|
| Decision ID | `TECH-DEC-{FEATURE}-{NN}` |
| Architecture intent | 技术无关语义和 guarantee |
| Mechanism family | `MECH-*` |
| Effective stack | product/language/framework + exact version/config |
| Evidence | manifest/lock/runtime/official spec/experiment |
| Candidate implementations | 当前 stack 可用候选 |
| Integration constraints | compiler/framework/data-access/driver/migration |
| Selected implementation | 选择及依据 |
| Fallback | 能力不可用时 |
| Environment compatibility | local/test/staging/prod |
| Evolution | upgrade/rollback/revalidation |
| Verification | 如何证明实际语义 |
