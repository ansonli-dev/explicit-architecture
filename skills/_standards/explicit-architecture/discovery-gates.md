# 设计输入与发现门禁

本文件规定 Target 生成和审查必须从 Canonical Service As-Is、Capability As-Is 和外部证据取得哪些设计依据，以及证据不足时如何裁决。业务服务事实和公共能力事实分别回到各自的自然 Owner Current。

## 目录

- [DISC-01 消费共享能力基线](#disc-01-消费共享能力基线)
- [DISC-02 裁决能力适配方式](#disc-02-裁决能力适配方式)
- [DISC-03 使用有效外部合同](#disc-03-使用有效外部合同)
- [DISC-04 使用真实外部流程](#disc-04-使用真实外部流程)
- [DISC-05 执行设计就绪门禁](#disc-05-执行设计就绪门禁)
- [证据状态](#证据状态)

## DISC-01 消费共享能力基线

设计或审查任何非领域机制前，必须组合两类事实：

从能力 Owner 的 Canonical Capability As-Is 取得：

- capability ID、Ref Profile、`{capability-current-ref}@sha`、module/effective version 和 owner；
- contract、guarantee 和 error semantics；
- non-guarantee、default、state/lifecycle 和 extension point；
- compatibility、migration、已确认 consumers；
- limitations、known exception 和 confidence。

从目标服务 Canonical As-Is 取得：

- 实际 resolved/effective version 和 environment；
- 生效 configuration、default override 和 local extension；
- 本地 Adapter/Policy/Mapper 和采用限制；
- Pending Develop/Feature Delta 中的升级或替换。

必须将服务 resolved version 与 Capability As-Is 记录的 effective version/compatibility 对照；不能把能力 Current 的 guarantee 无依据地套到不匹配版本。当前版本不可定位时视为 `CAPABILITY_BASELINE_GAP`。

中央 `CAPABILITY_CATALOG` 只用于发现 owner、repository 和 Canonical Capability As-Is 入口；不能替代任一 Current。

不得把“仓库搜索未命中”当作能力不存在，也不得临时建立另一份不受治理的能力清单。能力 Owner Baseline 缺失或过期时报告 `CAPABILITY_BASELINE_GAP`；服务采用事实缺失或过期时报告 `BASELINE_GAP`。

能力存在不表示适合当前需求；名字或结构相似也不表示具有同一 guarantee。

## DISC-02 裁决能力适配方式

Target 设计按以下顺序裁决，Spec 和业务代码审查验证：

1. `REUSE_AS_IS`：现有 contract 和 guarantee 已满足；
2. `CONFIGURE`：稳定配置或 Policy 即可满足；
3. `COMPOSE`：组合已有能力，不改变稳定核心；
4. `EXTEND_SHARED`：扩展已有能力的稳定变化轴；
5. `WRAP_LOCAL_ADAPTER`：公共机制复用，本地只保留业务适配；
6. `REPLACE_OR_UPGRADE`：方向正确但实现或版本不足；
7. `NEW_SHARED_CAPABILITY`：缺少独立、去业务化且可治理的机制；
8. `LOCAL_ONLY`：语义、状态或生命周期只属于当前 bounded context。

裁决必须说明 requirement、required guarantee、fit/gap、为何前一个更轻选择不足、consumer impact、Target owner、服务内适配点、验证和回滚。

禁止：

- 未消费 Capability As-Is 和服务采用事实就新增本地可靠性机制；
- 因已有能力不完整而绕过并平行建设；
- 为迁就公共库把业务语义塞入通用 API；
- 只按代码复用率决定公共化；
- 把 consumer 的 Aggregate、业务枚举或状态机放进公共能力。

剩余缺口是否公共化，继续使用 `capability-generalization.md`。

## DISC-03 使用有效外部合同

涉及第三方、跨服务或组织边界时，必须从 External Integration Dossier 取得当前有效的：

- owner、contract name、version、environment 和 evidence date；
- operation/channel/schema 与字段语义；
- authentication、correlation、idempotency 和 replay protection；
- error、timeout、ordering、delivery、duplicate 和 acknowledgement；
- callback/query/cancel/compensation；
- compatibility、deprecation 和环境差异。

中央 `INTEGRATION_CATALOG` 只用于发现 contract/process owner 和资料入口，不能替代当前有效合同和流程证据。

现有 client、SDK 或 Adapter 只能作为 Current Evidence，不能单独证明 Target contract。可变在线合同必须绑定版本或稳定引用。

## DISC-04 使用真实外部流程

字段合同之外，必须取得：

- 谁拥有最终事实；
- accepted、processing、completed、rejected、cancelled、expired 和 unknown 的真实含义；
- 同步响应、异步回调、查询和人工操作的关系；
- 哪些外部事实改变本地权利、义务或合法行为；
- timeout 后是否可能成功；
- duplicate、out-of-order、partial success 和版本共存；
- authoritative query、reconciliation、compensation 和人工恢复。

本地只映射改变本地业务语义的外部事实，不复制完整外部状态机。协议 correlation、投递和路由状态保留在外层。

## DISC-05 执行设计就绪门禁

对每个外部依赖或关键 Baseline 标记：

- `READY`：证据足以支撑 Target；
- `PARTIAL`：可完成技术中立语义，部分 Adapter/contract 决定待确认；
- `CONFLICTING`：来源在语义或 guarantee 上冲突；
- `UNAVAILABLE`：关键证据不可取得。

以下缺失会改变设计时，相关部分必须 `BLOCKED`：

- authority、identity 或 scope 会改变 Aggregate 或引用；
- 外部状态含义会改变本地状态机；
- accepted/completed/unknown 会改变业务结果；
- idempotency、callback 或 query 会改变恢复机制；
- contract/version 会改变协议或兼容；
- cancel/compensation 会改变端到端流程；
- Shared Capability 的 guarantee 或 extension point 会改变能力归属。

`PARTIAL` 时可以继续业务目标、本地权利义务、抽象 Port 和技术中立 failure model；不得伪造 endpoint、字段、状态、错误码、时序或版本能力。

## 证据状态

- `CONFIRMED`：有当前有效、可定位的一手证据；
- `INFERRED`：由多个证据推导，记录推理和失效条件；
- `UNKNOWN`：证据缺失或来源冲突。

外部合同、共享能力版本或运行配置发生变化时，重新确认相关输入。旧证据不能自动成为永久事实。
