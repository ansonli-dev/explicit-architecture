# 架构决策推理框架

本框架用于处理案例没有覆盖的新问题。不要根据业务名词匹配既有答案；从设计力、权威范围、失败模型和生命周期推导责任与机制。

## 目录

- [规范性级别](#规范性级别)
- [推理总流程](#推理总流程)
- [DR-01 恢复真实目标](#dr-01-恢复真实目标)
- [DR-02 分类设计力](#dr-02-分类设计力)
- [DR-03 确定权威、范围和时点](#dr-03-确定权威范围和时点)
- [DR-04 建立失败模型](#dr-04-建立失败模型)
- [DR-05 分配语义、决策和机制](#dr-05-分配语义决策和机制)
- [方案形成与裁决回路](#方案形成与裁决回路)
- [DR-06 按保障选择机制](#dr-06-按保障选择机制)
- [DR-07 标准路径测试](#dr-07-标准路径测试)
- [DR-08 反事实测试](#dr-08-反事实测试)
- [DR-09 最小性测试](#dr-09-最小性测试)
- [DR-10 公共能力透镜](#dr-10-公共能力透镜)
- [DR-11 演进与可逆性](#dr-11-演进与可逆性)
- [DR-12 证据闭环](#dr-12-证据闭环)
- [设计质量裁决顺序](#设计质量裁决顺序)
- [Architecture Decision Record](#architecture-decision-record)

## 规范性级别

- **MUST**：违反即不能批准，除非存在明确 `APPROVED_EXCEPTION`。
- **SHOULD**：默认遵守；偏离时必须记录具体设计力、代价和验证。
- **MAY**：由上下文选择，不应被审查工具机械要求。

规则 ID 是 As-Is、生成、Spec 审查和代码审查的稳定引用。引用前读取 `rule-index.md` 并复制精确 ID；具体 feature 决定使用 `DEC-*`，不得编造标准 ID。案例只能解释规则，不能限定规则适用范围。开始 Feature 推理前先读取 `as-is-consumption-contract.md` 和适用的 `DISC-*`。

## 推理总流程

```text
Need and business outcome
  → Verified As-Is and pending branch deltas
  → Existing shared capability evidence
  → External contract and process evidence
  → Design forces
  → Authority / scope / enforcement point
  → Failure model
  → Semantic owner / decision owner / mechanism owner
  → Mechanism family
  → Effective technology capability profile
  → Concrete implementation
  → Standard-path and counterfactual tests
  → Minimal artifacts
  → Capability and evolution decisions
  → Verification evidence
```

在完成失败模型前，不创建技术类、表、事件或公共抽象。

## DR-01 恢复真实目标

**MUST** 从需求表述中分离：

- 业务结果；
- 被保护的权利、义务或事实；
- 不允许出现的状态；
- 可接受的中间状态；
- 成功、拒绝、未知和恢复的用户可见含义；
- 非功能约束；
- 当前实现带来的偶然限制。

涉及已有共享机制时，以 `DISC-01` Baseline 为 Current Evidence；涉及外部系统时，以 `DISC-03/04` 取得的合同与流程为边界依据。未取得的关键事实标记未知，不用本地实现猜测替代。

“需要一个状态/服务/表/事件”可能只是请求者提出的实现。先追问它试图保障什么，再决定产物。

输出一个不带技术方案的目标陈述：

```text
当 {{TRIGGER}} 发生时，系统必须在 {{SCOPE}} 内保证 {{INVARIANT_OR_OUTCOME}}，
最晚于 {{EFFECTIVE_POINT}} 成立；发生 {{FAILURE}} 时必须达到 {{RECOVERY_OUTCOME}}。
```

## DR-02 分类设计力

**MUST** 识别所有适用设计力。一个需求通常同时包含多个力，不要用一个模式包办。下表是发现提示，不是封闭枚举；遇到新的法规、时间、组织、资源或技术约束时，仍按 authority、scope、effective point、failure model 和 lifecycle 轴分析。

| Design Force | 核心问题 | 常见遗漏 |
|---|---|---|
| Business semantics | 什么具有业务含义？ | 用技术状态代替业务语言 |
| Invariant / consistency | 哪些状态绝不能同时成立？ | 只考虑单线程 |
| Concurrency | 多个写入者如何竞争？ | 先查后写 |
| Coordination | 多步骤、多人或多系统如何推进？ | 把长流程塞入 Aggregate |
| Delivery | 意图或事实如何可靠到达？ | 把发布当普通调用 |
| Idempotency | 同一意图重复发生会怎样？ | 只按消息 ID 去重 |
| Recovery | 崩溃后从哪里继续？ | 内存重试、吞异常 |
| Integration translation | 外部事实如何成为本地意义？ | 复制完整外部状态机 |
| Projection / performance | 哪些表示为读取或性能派生？ | 反向污染 Domain |
| Security / authority | 谁可以观察或改变事实？ | 把授权散在 Handler |
| Evolution | 新旧版本如何共存和退出？ | 永久双写、无删除条件 |
| Operational control | 如何观测、限流、暂停、恢复？ | 机制不可运营 |
| Reuse / governance | 这是领域概念还是公共机制？ | 以代码相似度泛化 |

未出现在案例中的需求仍可通过这些设计力进入同一推理流程。

## DR-03 确定权威、范围和时点

**MUST** 对每项约束确定三个轴：

### Authority

- 哪个系统拥有事实？
- 本地保存的是权威事实、观察结果、引用还是缓存？
- 哪个参与者可以作出最终决定？

### Scope

- 单值；
- 单对象；
- 单 Aggregate；
- 同数据库跨 Aggregate；
- 单服务跨存储；
- 跨服务；
- 跨组织或第三方。

机制保障范围小于不变量范围时，方案无效。Aggregate 内检查无法原子保障全库唯一；本地事务无法保障远程副作用。

### Effective Point

- 调用时；
- Domain 行为返回时；
- 权威存储的原子操作完成后；
- transaction commit 时；
- 消息持久化时；
- 消费者确认时；
- 最终一致窗口结束时；
- 对账或人工确认后。

**MUST** 说明在有效时点之前允许哪些中间状态。

## DR-04 建立失败模型

**MUST** 先声明需要处理的失败，再选择技术：

- 同时提交；
- 重复请求；
- 请求超时但结果已发生；
- 进程在任意持久化边界前后崩溃；
- 网络分区；
- 消息重复、乱序、延迟或丢失；
- 下游暂时失败或永久拒绝；
- schema/version 不兼容；
- 数据迁移只完成一部分；
- 人工操作与自动流程竞争；
- 时钟偏差、配额耗尽或资源争用；
- 第三方给出冲突或不可识别状态。

对每个失败标记：

- prevent：必须阻止；
- detect：允许发生但必须检测；
- recover：必须自动或人工恢复；
- tolerate：在界限内可接受；
- exclude：有证据证明不在范围内。

没有失败模型的“可靠”“幂等”“一致”不构成设计。

## DR-05 分配语义、决策和机制

**MUST** 分开记录：

- **Semantic owner**：定义业务含义；
- **Decision owner**：根据已知事实作出业务选择；
- **Mechanism owner**：在正确范围和时点提供技术保障；
- **Operational owner**：监控、恢复和演进机制。

四者可以不同。Domain 拥有语义，不代表 Domain 必须保存技术字段；数据库提供保障，不代表数据库拥有业务规则。

每层只记录自己的责任，使用 handoff 连接，不复制实现。

## 方案形成与裁决回路

`to-spec` 必须用本节形成方案，`review-spec` 必须用本节复核方案。只对会改变边界、权威、状态机、保障、公共能力、外部合同或高代价演进的非平凡决定完整执行；不要给简单、可逆的局部选择制造假仪式。

1. **建立 Decision Contract**：记录 Need、invariant/outcome、authority、scope/effective point、failure model、design forces、Current evidence、evolution constraints 和 required evidence。改变关键设计的未知必须显式标记。
2. **生成实质候选**：候选至少在 responsibility placement、mechanism family、effective point、state/artifact impact 或 evolution path 上有实质差异。可行时加入“保持 Current / 最小变化”基准；只有一个候选可行时记录标准、物理约束或证据充分的排除理由，不制造只改类名或参数的假选项。
3. **执行硬淘汰**：候选出现以下任一情况即 `REJECTED`；关键证据未知则 `BLOCKED`，不得进入低优先级比较：
   - authority、scope 或 effective point 小于 required guarantee；
   - 关键 failure 未被 prevent/detect/recover/tolerate/exclude；
   - 违反 mandatory Standard、approved decision、依赖方向或自然 owner；
   - 依赖未验证的合同、版本、环境、移动 ref、隐藏顺序、flush、内存状态或单实例；
   - 产生第二事实源、污染内层、无法观察恢复，或高风险变化不可迁移回滚。
4. **有序裁决**：对存活候选使用本文件的[设计质量裁决顺序](#设计质量裁决顺序)，逐项记录关键差异和证据，不计算会掩盖高优先级缺陷的加权总分。高优先级存在 `UNKNOWN` 时不得选中。
5. **攻击并简化**：对暂选方案执行 DR-07、DR-08、DR-09、DR-11 和 DR-12。失败时回到 Contract 或候选集；通过正确性门禁后，才删除重复状态、判断、机制和无独立职责的产物。
6. **记录裁决**：保留所有候选的 `SELECTED / REJECTED / BLOCKED`、理由和证据，并记录 selected mechanism、residual risk、fallback、invalidation condition 与 verification。后续分层设计只展开选中方案，不重新发明决定。

生产者应证明“为何选择”；审查者应根据同一 Contract 独立重放硬门禁和相关攻击，而不是只确认文档中存在一张比较表。

## DR-06 按保障选择机制

**MUST** 从所需保障反推机制族，不从框架 API 或既有类名正推设计。

选择前回答：

1. 需要 prevention、detection、recovery 还是 compensation？
2. 保障范围在哪里？
3. 最晚何时成立？
4. 权威状态存在哪里？
5. 哪种机制在该范围内具有原子性或持久性？
6. 机制失败后是否可观察和恢复？

决定涉及一致性、并发、事务、异步副作用、重试、外部交互、长流程或技术派生状态时，读取 `mechanism-selection.md` 选择机制族；需要选择具体实现时再读取 `technology-selection.md`，根据相关 Service/Capability Current 中的有效 stack、发布范围和精确版本裁决。多个 failure force 通常需要组合机制；例如可靠投递不自动等于幂等消费，重试也不自动等于可恢复。

## DR-07 标准路径测试

**MUST** 检查方案能否通过框架和团队的普通路径成立：

- 普通 load → behavior → save；
- 普通 transaction commit；
- 普通 publish/consume 扩展点；
- 普通 rolling deployment；
- 普通 retry/recovery 运维流程。

以下方案 **SHOULD NOT** 进入 Target：

- 依赖显式 flush；
- 依赖调用者记住特殊顺序；
- 新入口必须复制隐藏判断；
- 正确性依赖当前 data-access/unit-of-work 的偶然写入排序；
- 依赖线程本地、内存标记或单实例；
- 需要绕过统一 Bus、Persistence 或既定可靠性扩展点；
- 用大段注释维持时序约定。

若底层机制与标准路径不兼容，优先在 Infrastructure 改变技术表示、约束形状或 Adapter 实现，不把妥协推入 Domain。

## DR-08 反事实测试

**MUST** 用未出现在当前故事中的变化攻击设计：

1. 新增第二个入口，规则是否仍成立？
2. 两个请求同时发生，谁裁决？
3. 在每个 I/O 前后崩溃，能否判断发生了什么？
4. 消息重复或乱序，业务结果是否稳定？
5. 第三方超时但实际成功，本地如何收敛？
6. 新旧版本同时运行，合同是否兼容？
7. 更换 data-access 实现或消息客户端，Domain 是否改变？
8. 当前业务名词替换后，机制是否仍完整？
9. 删除新增中间类，真正丢失了什么？
10. 数据规模或消费者数量增加，权威和保障范围是否变化？

不能通过相关反事实测试的方案只是当前 happy path 的实现。

## DR-09 最小性测试

**MUST** 对每个产物执行：

- natural owner：现有谁最自然拥有该职责？
- information test：独立类型保存了什么设计信息？
- lifecycle test：是否有自己的状态或生命周期？
- boundary test：是否隔离真实依赖或变化轴？
- deletion test：合回自然所有者会破坏什么？

只减少几行代码、集中 switch、推测未来复用或包装现有接口，不足以创建新概念。

## DR-10 公共能力透镜

**MUST** 对每个非领域机制执行一次公共能力判断，而不是等代码重复。

先使用 `DISC-01/02` 判断现有能力的 fit/gap 和复用关系，再读取 `capability-generalization.md` 判断剩余缺口。结果只能是：

- `LOCAL_POLICY`：强依赖当前 bounded context；
- `LOCAL_ADAPTER`：机制公共，但本地只拥有适配；
- `SHARED_CAPABILITY_CANDIDATE`：需要平台/seedwork 设计；
- `SHARED_KERNEL_CANDIDATE`：领域语义真正共同拥有；
- `DO_NOT_GENERALIZE`：抽象成本或语义差异超过收益。

## DR-11 演进与可逆性

**MUST** 判断：

- Current 是否已有消费者、数据或运行合同；
- Target 能否增量引入；
- 哪些决定不可逆或高成本；
- 如何观察新机制正确；
- 如何回滚；
- 临时产物何时删除；
- 公共能力升级是否破坏现有消费者。

在信息不足时偏好可逆决定，不以“以后再说”代替删除条件。

## DR-12 证据闭环

**MUST** 为关键决定定义证据：

- Domain 行为测试；
- 真实数据库并发/约束测试；
- failure injection；
- contract compatibility；
- restart/resume；
- migration/rollback rehearsal；
- observability signal；
- operational recovery procedure。

证据必须验证所选机制真正承诺的范围和时点。Mock 不能证明数据库原子性，单次 happy path 不能证明幂等或恢复。

## 设计质量裁决顺序

多个方案都能工作时，按以下顺序裁决：

1. 正确覆盖所需范围和失败模型；
2. 权威来源和事实所有权单一；
3. 保持依赖方向和层自治；
4. 通过标准路径，无隐藏时序；
5. 状态和产物最少；
6. 失败可观察、可恢复；
7. 演进可兼容、可回滚；
8. 机制可被复用，策略仍由业务拥有；
9. 团队理解和运维成本合理；
10. 代码量或局部实现便利。

“更少代码”只有在前九项相当时才是决定因素。

## Architecture Decision Record

对影响边界、一致性、可靠性、公共能力或不可逆演进的决定记录：

| Field | Content |
|---|---|
| Decision ID | `ADR/DR-*` |
| Need | 不带实现的目标 |
| Design forces | 适用 force |
| Authority / scope / point | 权威、范围、有效时点 |
| Failure model | prevent/detect/recover/tolerate/exclude |
| Responsibility | semantic/decision/mechanism/operational owners |
| Chosen mechanism | 机制族及组合 |
| Discovery evidence | 共享能力 Baseline；适用时包含外部 contract/process/readiness |
| Technology decision | 有效 stack、版本证据、实现与 fallback |
| Rejected alternatives | 因何不覆盖 force，而非“个人偏好” |
| Residual / fallback / invalidation | 剩余风险、降级或回退路径、重新裁决条件 |
| Standard-path result | 是否需要特殊顺序 |
| Capability outcome | reuse/configure/compose/extend/replace/new/local/kernel |
| Evolution | compatibility、rollback、removal |
| Evidence | 如何证明 |

简单、可逆且无架构影响的决定不必创建 ADR，但仍应用 DR-01 至 DR-09。
