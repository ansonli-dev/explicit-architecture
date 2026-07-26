# 公共能力 As-Is 消费契约

本文件定义公共能力 Owner 仓库向设计和审查流程提供的 Current 资产。适用于共享库、内部 SDK、Starter 和 Framework Module；中央能力目录只负责发现，业务服务 As-Is 只记录实际采用情况。默认 Current ref 为 `develop`；允许按 `baseline-ref-profile.md` 显式映射到仓库受治理的稳定 ref。

## 目录

- [CASIS-01 回到能力自然所有者](#casis-01-回到能力自然所有者)
- [CASIS-02 固定能力稳定基线](#casis-02-固定能力稳定基线)
- [CASIS-03 只读取已解析的能力 Current Ref](#casis-03-只读取已解析的能力-current-ref)
- [CASIS-04 按能力契约记录 Current](#casis-04-按能力契约记录-current)
- [CASIS-05 分类能力事实差异](#casis-05-分类能力事实差异)
- [CASIS-06 根据能力 Current Ref 更新基线](#casis-06-根据能力-current-ref-更新基线)
- [CASIS-07 保持单一事实所有者](#casis-07-保持单一事实所有者)
- [最小输入包](#最小输入包)

## CASIS-01 回到能力自然所有者

公共能力的精确 contract、guarantee、non-guarantee、有效版本、默认行为、扩展点、兼容和限制由能力 Owner 仓库的 Capability As-Is 拥有。

- 中央 `CAPABILITY_CATALOG` 只记录稳定摘要、Owner、仓库和权威入口；
- 业务服务 As-Is 只记录 resolved version、配置、本地扩展和采用偏差；
- Feature Spec 只引用并摘录相关事实。

不同资产冲突时标记证据冲突，不静默复制或覆盖。

## CASIS-02 固定能力稳定基线

默认 Profile 将 `develop` 视为稳定且已发布状态。先解析 `baseline-ref-profile.md` 的 `capability.current_ref`；Canonical Capability As-Is 必须绑定：

- repository identity；
- resolved current ref role 与实际 ref 名称；
- immutable full commit SHA；
- status；
- evidence timestamp；
- evidence scope；
- effective SDK/module version（仓库声明时记录）。

移动分支名不能替代 commit。制品仓库、release tag、test 分支和部署环境不是本 Profile 的必需证据。

## CASIS-03 只读取已解析的能力 Current Ref

Capability Current 只从固定后的 `{capability-current-ref}@sha` 取得；默认值为 `develop@sha`：

- 不读取 feature/test/release 分支来补充 Current；
- 不把 working tree 未提交变化写入 Current；
- 不计算 feature、test 或 release candidate Delta；
- 不因其他分支存在更新而提前改变 Baseline。

需要读取文件时使用 `git show <full-sha>:<path>` 或基于该 SHA 的独立 worktree，不为扫描切换用户当前分支。

## CASIS-04 按能力契约记录 Current

Capability As-Is 按适用范围记录：

- module/artifact dependency map；
- capability identity、purpose 和 boundary；
- contract、guarantee、non-guarantee 和 failure semantics；
- public API/SPI、configuration、default behavior 和 extension point；
- state/lifecycle、schema、worker、operation 和 recovery（真实存在时）；
- effective version、compatibility、deprecation 和 migration（适用时）；
- 已确认 consumer evidence 或消费者发现方式；
- limitations、known deviation、confidence 和 traceability。

不要套用业务服务的 Domain/Application/Interfaces/Infrastructure 文档结构，不为纯库创建虚假 Runtime，也不把 roadmap 或未来设计写成 Current。

## CASIS-05 分类能力事实差异

使用：

- `NO_DRIFT`
- `CAPABILITY_AS_IS_STALE`
- `IMPLEMENTATION_DRIFT`
- `CONTRACT_GAP`
- `EVIDENCE_GAP`

Current ref 中存在代码不自动证明某项 guarantee；必须由 public contract、实现路径和测试共同支撑。

## CASIS-06 根据能力 Current Ref 更新基线

Capability As-Is 更新必须满足：

- 所有事实来自同一个固定的 `{capability-current-ref}@sha`；
- module、contract、defaults、extension 和 limitations 有对应证据；
- 适用的 compatibility/migration 说明与 fixed current ref 一致；
- 未确认行为标记 confidence/gap；
- Manifest 和 traceability 更新为新的 full SHA；
- 新 Baseline 链接 superseded Baseline。

本 Profile 不设置独立发布提升流程。解析后的 `{capability-current-ref}@sha` 是稳定 Current，文档更新只是让 Capability As-Is 与该 Current 对齐。

## CASIS-07 保持单一事实所有者

Canonical Capability As-Is 只有一个受治理写入流程。其他产物可以：

- 引用 Capability Baseline；
- 报告 `CAPABILITY_BASELINE_GAP`；
- 提交 Capability As-Is Delta Candidate；
- 不直接复制或修改 Canonical Capability As-Is。

## 最小输入包

| Input | Required content |
|---|---|
| Capability Baseline | baseline ID、Ref Profile、`{capability-current-ref}@full-sha`、status、evidence timestamp |
| Relevant capabilities | module/version、contract、guarantee、non-guarantee、defaults、extension、limitations |
| Compatibility | 当前支持、弃用和 migration；不适用时明确省略 |
| Consumers | 已确认 consumer/version 或发现方式 |
| Gaps | stale、contract、compatibility 或 evidence gap |

Capability As-Is 缺失、与绑定的 `{capability-current-ref}@sha` 明显不符，或缺少会改变设计的 guarantee/extension evidence 时，消费方输出 `CAPABILITY_BASELINE_GAP`，不得自行重建后冒充权威输入。
