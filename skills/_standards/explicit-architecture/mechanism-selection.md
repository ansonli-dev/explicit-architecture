# 保障机制选择

本文件是机制族选择指南，不是产品或框架清单。先执行 `decision-framework.md`，再根据所需范围、有效时点和失败模型组合机制。

## 目录

- [选择原则](#选择原则)
- [机制族总览](#机制族总览)
- [MECH-01 声明式不变量](#mech-01-声明式不变量)
- [MECH-02 原子条件变更](#mech-02-原子条件变更)
- [MECH-03 冲突检测](#mech-03-冲突检测)
- [MECH-04 串行化](#mech-04-串行化)
- [MECH-05 本地事务](#mech-05-本地事务)
- [MECH-06 持久化意图](#mech-06-持久化意图)
- [MECH-07 幂等效果](#mech-07-幂等效果)
- [MECH-08 重试与续跑](#mech-08-重试与续跑)
- [MECH-09 对账与修复](#mech-09-对账与修复)
- [MECH-10 补偿](#mech-10-补偿)
- [MECH-11 派生表示](#mech-11-派生表示)
- [权威持久化保障推导](#权威持久化保障推导)
- [副作用机制推导](#副作用机制推导)
- [外部交互机制推导](#外部交互机制推导)
- [组合与门禁](#组合与门禁)

## 选择原则

1. **MUST** 选择能覆盖不变量实际范围的机制。
2. **MUST** 选择在要求时点具有原子性或持久性的机制。
3. **MUST** 明确机制只能 prevent、detect、recover 还是 compensate。
4. **SHOULD** 优先由权威状态所在组件执行保障。
5. **SHOULD** 优先声明式、原子、持久和标准路径。
6. **MUST NOT** 用重试替代幂等，用幂等替代可靠投递，用本地事务替代跨服务一致性。
7. **MAY** 组合多个机制，但每个机制必须有独立责任。

## 机制族总览

| Mechanism | 主要能力 | 适用范围 | 不能单独解决 |
|---|---|---|---|
| Declarative invariant | 阻止非法持久状态 | 权威数据存储 | 远程副作用、恢复 |
| Atomic guarded mutation | 原子判断并变更 | 单权威存储 | 长流程、跨服务 |
| Conflict detection | 检测并发覆盖 | 对象/Aggregate | 自动恢复、业务合并 |
| Serialization | 排队或互斥执行 | 锁/分区覆盖范围 | 崩溃恢复、远程一致性 |
| Local transaction | 原子提交多个本地变化 | 同事务资源 | 外部 I/O |
| Durable intent/log | 提交业务状态与待执行意图 | 本地事务 + 异步 | 消费幂等、业务补偿 |
| Idempotent effect | 重复执行结果稳定 | 幂等键覆盖范围 | 投递丢失、乱序语义 |
| Retry/resume | 暂时失败后继续 | 可持久恢复单元 | 永久错误、非幂等效果 |
| Reconciliation | 比较权威事实并发现漂移 | 跨边界/最终一致 | 即时阻止非法状态 |
| Compensation | 业务上抵销已发生效果 | 跨事务流程 | 真正回滚外部历史 |
| Derived representation | 为查询/约束/协议派生形状 | 外层表示 | 独立业务事实 |

## MECH-01 声明式不变量

当规则可表达为权威持久状态上的谓词，且所有写入口最终经过同一存储时，**SHOULD** 优先使用声明式约束。

检查：

- predicate 是否覆盖真实业务范围；
- constraint 在 statement 还是 commit 生效；
- null、unknown、软删除和历史状态语义；
- 多行/多表是否仍能原子表达；
- migration 前是否存在脏数据；
- 错误能否稳定翻译。

声明式约束负责最终保障，Domain 仍表达业务语义。不要为了持久化表示把影子键、生成/计算字段或约束状态加入 Domain。

不适用：

- 权威状态跨多个不可原子提交的系统；
- 规则需要外部实时事实；
- 冲突需要业务比较和合并；
- 合法性依赖复杂长流程，而非持久状态谓词。

## MECH-02 原子条件变更

当行为可以表示为“仅当当前状态满足 predicate 时更新”，优先使用 compare-and-set、条件更新或原子计数等机制。

适合：

- 状态推进；
- 配额/库存扣减；
- lease 获取；
- 一次性消费；
- 有界资源分配。

必须定义：

- predicate；
- update；
- zero-row / conflict 的稳定业务意义；
- 重试是否安全；
- 返回的新状态或 revision。

不要先读取再依赖应用层判断，除非最终写入仍带相同条件。

## MECH-03 冲突检测

当多个写者可以基于旧快照作出决定，使用 version/revision/etag 检测 lost update。

冲突检测只说明“有人先改了”，不自动决定：

- 是否重试；
- 是否合并；
- 返回何种业务错误；
- 是否重新执行外部调用。

**MUST** 保留从读取到写入的 expected revision。保存时重新读取最新实体再覆盖，会绕过冲突检测。

适合低冲突或可重试的 Aggregate。高冲突、昂贵重算或严格顺序场景需要重新评估串行化或原子条件变更。

## MECH-04 串行化

当操作不可交换、冲突代价高，或必须在同一 key 上严格顺序执行时，可使用：

- authoritative-store row/key/advisory lock；
- partition/key ordered queue；
- lease；
- single-writer ownership。

必须说明：

- 锁/分区 key；
- 覆盖范围；
- timeout；
- owner crash 后释放；
- starvation/deadlock；
- 多实例下是否仍成立；
- 运维暂停和恢复。

不要用进程内锁保障多实例或跨进程不变量。

## MECH-05 本地事务

本地事务只覆盖同一事务管理器能原子提交的资源。

**SHOULD** 保持事务：

- 范围最小；
- 不包含远程调用、邮件或不可回滚外部 I/O；
- 与 Domain 决策使用的状态一致；
- 能把业务变化和必要持久意图一起提交。

跨 Aggregate 同库原子变更需要先复核 Aggregate 边界，再明确事务例外。跨服务不使用“分布式事务假设”掩盖补偿或对账。

## MECH-06 持久化意图

当业务状态提交后必须可靠执行异步副作用，将“待执行意图”与业务变化原子持久化，再由独立执行机制处理。

一般形式：

```text
business state + durable intent
  → commit
  → claim
  → execute
  → acknowledge
  → retry / dead-letter / reconcile
```

必须定义：

- intent identity 和 payload；
- claim/lease；
- at-least-once implications；
- completion state；
- crash between execute/ack；
- retention/replay；
- schema evolution；
- operational recovery。

Outbox 是该机制族的一种实现，不是所有异步需求的唯一答案。

## MECH-07 幂等效果

幂等必须基于稳定的**业务操作身份**或效果身份，而不是默认使用每次重试都变化的 transport message ID。

选择幂等键时检查：

- 谁生成；
- 业务范围；
- 生命周期和保留期；
- 同 key 不同 payload 如何处理；
- 成功、处理中、失败是否缓存；
- 并发重复如何原子裁决；
- 下游是否原生支持。

幂等防止重复效果，不保证意图一定到达，也不解决乱序业务语义。

## MECH-08 重试与续跑

只对被分类为暂时失败、且操作幂等或可安全恢复的步骤重试。

可靠 retry/resume 需要：

- 持久化 retry unit；
- attempt、next-at、backoff 和 deadline；
- error classification；
- claim/lease 和并发 worker 语义；
- poison/dead-letter；
- pause/resume；
- metrics 和人工干预；
- 完成判定。

线程内循环、递归调用、吞异常或只依赖进程内 scheduler 不是崩溃可恢复机制。

## MECH-09 对账与修复

当跨系统结果可能未知、消息可能永久遗漏或人工会修改权威事实时，必须有对账：

```text
enumerate candidate
  → observe authoritative fact
  → compare expected vs actual
  → classify drift
  → repair automatically or create work item
  → record evidence
```

对账不是“最后兜底”的模糊承诺。必须定义：

- candidate 范围；
- 权威读取；
- 收敛 SLA；
- 修复权限；
- 重复修复幂等；
- 审计和告警。

## MECH-10 补偿

跨事务效果已经发生且无法技术回滚时，设计业务补偿。

补偿必须说明：

- 原效果的业务身份；
- 补偿触发条件；
- 补偿是否可能失败；
- 重复补偿；
- 部分补偿；
- 不可补偿时的人工流程；
- 补偿后的本地业务状态。

补偿不是数据库 rollback，也不保证恢复到历史上“从未发生”的状态。

## MECH-11 派生表示

当内层语义无法直接适配外层约束、查询或协议时，可以派生技术表示：

- 约束键；
- materialized/read model；
- search document；
- cache entry；
- vendor request/response；
- delivery metadata。

必须满足：

- 可从权威事实或技术过程推导；
- 没有独立业务决策；
- 由外层 Mapper/Adapter 拥有；
- 生命周期和重建方式明确；
- 不写回 Domain 成为第二事实源。

## 权威持久化保障推导

面对任何持久化约束问题，按以下顺序推导，不按案例关键词或产品名选择：

1. 把不变量写成权威数据上的 predicate。
2. 确定 predicate 在 statement 还是 commit 必须成立。
3. 确定写入集合和 data-access/storage operation 的实际过渡状态。
4. 判断当前权威存储的原生机制是否直接表达 predicate。
5. 若逻辑 predicate 与物理表示不匹配，在 Persistence Model 派生约束表示。
6. 验证所有写入口都经过约束。
7. 设计 constraint violation → 稳定错误语义。
8. 使用实际选定的存储产品、有效版本和标准 data-access 路径测试并发、过渡写入和迁移数据。

选择具体约束形状时比较：

- predicate/partial support；
- deferrability；
- null semantics；
- generated/expression/functional support；
- data-access/unit-of-work ordering；
- update/delete/insert 的中间状态；
- online migration；
- 可解释性和运维诊断；
- 是否保持普通 save/commit。

如果直接约束与框架标准路径冲突，**SHOULD** 优先改变 Infrastructure 表示，而不是要求 Handler flush、拆事务或给 Domain 增加技术状态。

## 副作用机制推导

先判断副作用性质：

| Nature | Default direction |
|---|---|
| 当前命令的主要目的，失败必须直接返回 | Handler 通过 Port 调用，且位于本地资源 transaction 外 |
| 本地业务状态变化的独立反应 | Domain Event → same-service driving listener |
| 跨服务事实传播 | Domain Event → durable outbox/integration event |
| cache/index 等纯技术反应 | Persistence Adapter 或 Infrastructure listener |
| 可延迟、需持久重试的工作 | durable task/retry capability |
| 跨时间多步骤业务流程 | Process Manager / Saga |

继续用失败模型检查：

- commit 前/后崩溃；
- execute 后 ack 前崩溃；
- 重复与乱序；
- 永久失败；
- 人工恢复；
- schema/handler evolution。

## 外部交互机制推导

对外部调用区分：

- command accepted；
- business completed；
- technically delivered；
- result unknown；
- callback observed；
- reconciled authoritative result。

不要把 transport success 当业务 success。稳定保存：

- local operation identity；
- external reference（若参与业务生命周期）；
- correlation/idempotency metadata（外层）；
- last authoritative observation；
- next recovery action。

外部状态只有改变本地权利义务时才进入 Domain；其余保持 Integration/Infrastructure observation。

## 组合与门禁

每个重要方案输出：

| Required force | Mechanism | Owner | Scope | Effective point | Failure covered | Residual risk | Evidence |
|---|---|---|---|---|---|---|---|

最终检查：

- 每个 failure 至少被 prevent/detect/recover/tolerate/exclude；
- 没有机制承担超出能力的承诺；
- 机制组合没有重复事实源；
- 不依赖隐藏顺序；
- 可以观测和人工恢复；
- 外层派生没有泄漏内层；
- 具体产品/框架只是机制实现，不是设计依据。
