# 公共能力泛化标准

本文件用于发现和设计公共机制，同时防止推测性抽象与错误 Shared Kernel。案例数量不是前提；机制完整性、变化轴和治理才是依据。

## 目录

- [Capability 与 Shared Kernel](#capability-与-shared-kernel)
- [GEN-01 去业务化](#gen-01-去业务化)
- [GEN-02 机制身份](#gen-02-机制身份)
- [GEN-03 稳定核心与变化轴](#gen-03-稳定核心与变化轴)
- [GEN-04 生命周期与状态所有权](#gen-04-生命周期与状态所有权)
- [GEN-05 第一个消费者](#gen-05-第一个消费者)
- [GEN-06 升级现有能力](#gen-06-升级现有能力)
- [GEN-07 API 与治理](#gen-07-api-与治理)
- [GEN-08 不泛化条件](#gen-08-不泛化条件)
- [GEN-09 Shared Kernel 门禁](#gen-09-shared-kernel-门禁)
- [Capability Decision Record](#capability-decision-record)

## Capability 与 Shared Kernel

区分：

- **Shared Capability**：可靠性、一致性、调度、协议、观测、资源管理等去业务化机制；
- **Local Policy/Adapter**：bounded context 提供的决策、Mapper、Payload、Handler、Policy 或配置；
- **Shared Kernel**：多个 bounded context 共同拥有的领域语义和模型。

公共能力可以有第一个消费者；Shared Kernel 不能因为代码重复或字段相同产生。

## GEN-01 去业务化

对候选能力执行：

1. 移除业务名词和实体名称；
2. 用机制语言重述输入、状态、输出和失败；
3. 判断是否仍有完整生命周期和价值；
4. 检查 API 是否仍依赖某个 bounded context 的状态/枚举/异常。

去业务化后问题仍完整，只是公共能力的必要条件，不是充分条件。

## GEN-02 机制身份

候选公共能力 **MUST** 有稳定机制身份：

- 解决哪类 design force；
- 提供什么 guarantee；
- 不提供什么；
- 拥有哪些状态；
- 从何恢复；
- 如何观测和运维；
- 与相邻能力的边界。

“共享工具方法”“少写代码”或“以后可能复用”不构成机制身份。

机制命名应能脱离第一个消费者，例如“durable retry execution”而不是“order event retry”。

## GEN-03 稳定核心与变化轴

**MUST** 分离公共稳定核心和消费者变化轴。

常见公共核心：

- durable state machine；
- claim/lease；
- retry scheduling；
- backoff/deadline；
- deduplication；
- serialization/codec contract；
- observability；
- pause/resume/replay；
- retention/cleanup；
- upgrade/migration hooks。

常见本地变化轴：

- business payload；
- Handler；
- Domain/Integration Mapper；
- failure classification policy；
- authorization；
- business deadline；
- compensation decision；
- tenant/priority configuration。

公共 API 应暴露稳定扩展点，不要求 shared 模块理解业务枚举或 Aggregate。

## GEN-04 生命周期与状态所有权

公共能力只有在生命周期可独立治理时才成立：

- 谁创建状态；
- 谁 claim/execute/ack；
- 谁清理；
- schema 属于谁；
- 失败由谁值守；
- 如何暂停、恢复、重放；
- 如何升级；
- 消费者代码与平台状态如何兼容。

如果状态生命周期完全依附一个 Aggregate 且没有独立机制行为，更可能是本地模型或 Adapter。

## GEN-05 第一个消费者

不要求先出现两份重复代码。满足以下条件时，第一个消费者即可触发公共能力设计：

1. 问题是机制级，不含 bounded-context 语义；
2. 生命周期独立；
3. 稳定核心和本地变化轴已分开；
4. 公共 owner 和运维责任存在；
5. API 能服务合理的第二类消费者，而不预埋其业务概念；
6. 当前需求确实需要该机制，不是纯预测。

第一个消费者原则不能用于创建没有完整合同的“万能框架”。

## GEN-06 升级现有能力

先按 `discovery-gates.md` 消费 Capability As-Is 和目标服务采用事实。发现新需求时，判断是否是已有机制的相邻能力：

- 是否共享同一 durable state；
- 是否共享 claim/lease 和执行生命周期；
- 是否共享 failure classification、retry、dead-letter 和 recovery；
- 是否共享 observability 和运维操作；
- 是否只需新增稳定扩展点；
- 升级是否保持已有消费者兼容。

若答案大多为是，**SHOULD** 升级现有公共能力，而不是在服务内平行实现。

若新需求具有不同 guarantee、状态生命周期或运维 owner，则保持独立能力，避免“platform blob”。

## GEN-07 API 与治理

公共能力设计 **MUST** 定义：

- capability contract；
- extension point；
- compatibility policy；
- configuration ownership；
- data/schema migration；
- metrics、traces、logs；
- operational commands；
- consumer onboarding；
- deprecation；
- failure isolation 和 quota。

公共模块不包含：

- 业务 Aggregate、Value Object 或状态；
- 服务专属 endpoint/schema；
- 业务补偿决策；
- 供应商 DTO（除非能力本身就是该供应商 Adapter）。

## GEN-08 不泛化条件

标记 `DO_NOT_GENERALIZE`，当：

- 语义依赖单一 bounded context；
- 变化轴尚不清楚；
- 没有公共 owner；
- 机制状态无法独立治理；
- 抽象 API 需要大量业务回调才能工作；
- 当前需求可由现有自然所有者简单完成；
- 公共化会扩大 blast radius、发布耦合或兼容负担；
- 第二消费者只能靠猜测构造；
- 现有能力的扩展点足以解决，无需新 capability。

不泛化决定也应记录依据，防止后续重复争论。

## GEN-09 Shared Kernel 门禁

领域概念进入 Shared Kernel 必须同时满足：

- 相同业务语义；
- 相同不变量和运算；
- 相同生命周期；
- 相同变化方向；
- 共同语言；
- 共同所有权；
- 联合版本和发布治理；
- bounded context 接受耦合。

领域对象名称或结构相同不构成证据。若各 context 可独立演进，保留本地模型并在边界映射。

## Capability Decision Record

| Field | Content |
|---|---|
| Candidate | 机制名称 |
| Design force / guarantee | 解决什么、承诺什么 |
| De-businessized statement | 无业务名词描述 |
| Stable core | 公共状态和行为 |
| Local variation | Handler/Mapper/Policy/Payload/config |
| Lifecycle owner | 创建、执行、恢复、清理 |
| First/current consumers | 当前真实消费 |
| Existing capability relation | reuse / configure / compose / extend / replace / separate |
| Compatibility impact | 对现有消费者 |
| Operational model | observe/pause/resume/replay |
| Decision | local/shared candidate/kernel/do-not-generalize |
| Evidence | 测试和演进验证 |

Spec 生成必须记录候选和结论；Spec/代码审查只在公共化缺失会造成局部重复机制、错误 owner 或无法满足 guarantee 时将其提升为阻塞 finding。
