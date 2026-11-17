# 业务服务 As-Is 证据发现

本文件说明如何建立服务的 Shared Capability Consumption Snapshot 与 External Integration Dossier。公共能力的权威 Baseline 位于能力 Owner Current；本流程只证明当前服务实际采用什么。

## 目录

- [共享能力采用发现](#共享能力采用发现)
- [外部合同发现](#外部合同发现)
- [外部流程发现](#外部流程发现)
- [证据等级](#证据等级)
- [最小产出](#最小产出)

## 共享能力采用发现

先从中央 `CAPABILITY_CATALOG` 和能力 Owner 的 Capability As-Is 发现候选能力，再从当前服务确认实际采用：

- dependency manifest、lock、BOM/platform 和 resolved dependency；
- deployment/runtime classpath、image/package 和生效 configuration；
- import/call/registration/extension path；
- 本地 Adapter、Policy、Mapper、Handler 和 override；
- schema、state、worker 或 operational component 的本地启用情况；
- 相关 contract/compatibility test 和运行证据。

对每项采用关系追踪：

1. Capability Ref Profile、Baseline ID、resolved current-ref/full SHA、module/version 和 owner；
2. 服务实际 resolved version 与 Capability As-Is effective version/compatibility 的关系；
3. 生效 configuration、default override 和 extension point；
4. 本地调用路径、Adapter/Policy/Mapper 和运行组件；
5. 兼容、弃用、迁移和环境限制；
6. adoption evidence、confidence 和未确认行为。

服务 As-Is 记录 Capability Baseline ref、effective version/config、local use/extension、environment、limitations、evidence 和 confidence。contract、guarantee、non-guarantee、完整 lifecycle 和组织级 consumers 回到 Capability As-Is；名字或结构相似不能证明采用了同一能力。

Capability As-Is 缺失或与已解析版本无法对应时输出 `CAPABILITY_BASELINE_GAP`；不要在服务扫描中创建另一份权威能力说明。

## 外部合同发现

涉及第三方、跨服务或组织边界时，主动获取：

- owner、contract name、version、environment 和生效时间；
- operation、endpoint/channel、message/schema 或 file format；
- 字段语义、required/default/unknown value；
- authentication、signature、correlation 和 replay protection；
- idempotency、ordering、delivery、acknowledgement 和 duplicate semantics；
- error taxonomy、retryability、timeout、rate limit、quota 和 SLA；
- callback/webhook/query/cancel/compensation；
- compatibility、deprecation、change notification；
- sandbox、test 与 production 差异。

证据优先级：

1. 用户提供或已接受的正式合同、schema、协议和流程说明；
2. 当前版本的官方文档或可查询 metadata；
3. 双方接受的 ADR、contract test 或 schema registry；
4. 已生成 client/SDK、仓库快照和现有 Adapter；
5. sandbox 或受控实验；
6. 日志和流量观察。

后三级主要证明 Current Evidence，不能单方面扩大长期合同。在线合同记录取得日期、版本和稳定引用。

## 外部流程发现

字段合同之外继续确认：

- 谁发起、谁拥有最终事实；
- accepted、processing、completed、rejected、cancelled、expired、unknown 的真实含义；
- 同步响应、异步回调、查询和人工操作的关系；
- 哪个外部事实能够推进本地单据；
- 状态是否允许逆转、重复、跳转或晚到；
- timeout 后外部操作是否可能已经成功；
- authoritative query、reconciliation、cancel、refund、release 和 compensation；
- duplicate、out-of-order、partial success 和 bulk semantics；
- outage、maintenance、恢复重放、人工介入和争议处理；
- 新旧合同或流程版本如何共存。

现有 client 可能过时或只覆盖 happy path，不能单独作为 Target contract。关键资料不可访问时记录权限或文档依赖，不通过本地代码猜测。

## 证据等级

- `CONFIRMED`：当前有效且可定位的一手证据；
- `INFERRED`：由多个证据推导，并记录推理和失效条件；
- `UNKNOWN`：证据缺失或来源冲突。

每项事实记录 commit/environment 或 version/date。搜索未命中不等于不存在。

## 最小产出

### Shared Capability Consumption Snapshot

| Capability | Owner Baseline / contract | Effective version / environment | Local use / configuration / extension | Adoption limitations | Evidence | Confidence |
|---|---|---|---|---|---|---|
| {{CAPABILITY}} | {{OWNER_BASELINE_CONTRACT}} | {{VERSION_ENVIRONMENT}} | {{LOCAL_USE_CONFIGURATION_EXTENSION}} | {{LIMITATIONS}} | {{EVIDENCE}} | `{{CONFIDENCE}}` |

### External Integration Dossier

| External system | Contract / version | Process authority | Local use | Unknown / recovery | Readiness evidence | Evidence date |
|---|---|---|---|---|---|---|
| {{SYSTEM}} | {{CONTRACT_VERSION}} | {{AUTHORITY}} | {{LOCAL_USE}} | {{RECOVERY}} | {{READINESS_EVIDENCE}} | {{DATE}} |
