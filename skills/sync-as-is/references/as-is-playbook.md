# As-Is 操作手册

## 目录

- [Mode 选择](#mode-选择)
- [安全解析 Git 基线](#安全解析-git-基线)
- [证据收集](#证据收集)
- [中央上下文只读边界](#中央上下文只读边界)
- [分层重建](#分层重建)
- [Baseline Verify](#baseline-verify)
- [Post Delivery Promote](#post-delivery-promote)
- [退出门禁](#退出门禁)

## Mode 选择

| Condition | Mode |
|---|---|
| 新系统，无业务实现 Baseline | `GREENFIELD_BOOTSTRAP` |
| 既有代码，无可信 As-Is | `BROWNFIELD_RECONSTRUCT` |
| As-Is 已存在，需要检查差异 | `BASELINE_VERIFY` |
| 新 verification/candidate-ref commit 已通过测试 | `POST_DELIVERY_PROMOTE` |

不要在同一次运行中一边重建未知 Baseline，一边宣称完成 Promotion。先建立可信 previous state。

## 安全解析 Git 基线

先解析 Ref Profile。以下是默认 `test/develop/feature` Profile 的只读检查示例；自定义 Profile 替换对应 ref 名：

```text
git rev-parse --verify test
git rev-parse test^{commit}
git rev-parse develop^{commit}
git merge-base develop <feature-ref>
git diff --name-status <baseline-sha>..<develop-sha>
git diff --name-status <merge-base>..<feature-sha>
```

实际命令使用已验证 ref 并处理仓库的 remote/local 约定。不要：

- checkout/switch 用户分支；
- merge、rebase、reset；
- 默认 remote 已同步；
- 把 working tree 未提交变化算入某个 commit；
- 用 branch HEAD 代替已验证 SHA。

若 resolved verification ref HEAD 正在验证：

```text
Canonical = last verified verification-ref SHA
Candidate = current verification/candidate-ref SHA
```

需要从其他 ref 读取文件时，使用 `git show {sha}:{path}`；需要完整目录或构建验证时，使用独立临时 worktree，并在完成后按安全规则清理。

## 证据收集

按风险选择证据，不机械扫描所有文件：

### Landscape

- module/service boundaries；
- ownership 和 deployment；
- calls/events/data flow；
- authoritative systems。

### Shared Capabilities

- Capability As-Is Baseline ref 和 owner；
- 当前服务 resolved/effective version；
- 生效 configuration/default override/extension；
- 本地 Adapter/Policy/Mapper；
- 当前环境限制和采用偏差。

### Layered System Evidence

- actual business behavior location；
- handler/orchestration；
- inbound/outbound boundary；
- persistence/reliability/client；
- runtime/configuration。

### Integration

- official contract/process；
- implemented adapter；
- schema/version；
- callback/query/recovery；
- environment differences。

### Verification

- tests linked to guarantee；
- deployed commit；
- migration status；
- DC/test result；
- runtime observations。

每项事实写最窄充分证据引用。不要把搜索命中列表直接变成 As-Is。

## 中央上下文只读边界

中央 Architecture Context 只用于：

- 发现适用的系统边界、Standards、Patterns、ADR、owner 和资料入口；
- 解释已批准 Target；
- 对 observed Current 标记可能的 deviation 或 evidence gap。

先读 index，固定 commit/version 和实际文档 ID。不得：

- 把中央摘要当成服务 Current 的最终证据；
- 用 Pattern 美化 As-Is；
- 在服务 As-Is 同步中修改中央文档；
- 因中央文档与实现冲突就自动覆盖任一方。

冲突、过期、缺失或不可发现内容输出 Architecture Governance Candidate。

## 分层重建

恢复顺序：

```text
Landscape
  → Consumed Shared Capabilities
  → External Integrations
  → System vertical slices
  → Layered responsibilities
  → Runtime and evidence
```

对每个 vertical slice 追踪：

```text
input
  → boundary mapping
  → use-case orchestration
  → business decision
  → persistence/outbound mechanism
  → result/effect
```

As-Is 记录“实际在哪里”，Target Standard 只作为 deviation reference。不要移动责任或创建未来 artifact。

## Baseline Verify

比较四方：

| Source | Role |
|---|---|
| Existing As-Is | claimed current fact |
| Bound code/runtime | observed current fact |
| Approved Spec/ADR | authorized change |
| Verification evidence | proves effective reality |

裁决：

- As-Is 与 observed 一致：`NO_DRIFT`；
- observed 未经批准偏离：`IMPLEMENTATION_DRIFT`；
- approved + observed + verified：`AS_IS_STALE`；
- approved 但未验证：`APPROVED_CHANGE`；
- 明确迁移中：`TRANSITION_STATE`；
- deployed/runtime 与 branch 不同：`ENVIRONMENT_DRIFT`；
- 无法证明：`EVIDENCE_GAP`。

只报告事实关系；设计优劣不属于 As-Is 同步范围。

## Post Delivery Promote

Promotion checklist：

1. previous Baseline status 为 `VERIFIED`；
2. candidate 是 resolved verification/candidate ref 上的 immutable SHA；
3. candidate 已部署到声明 environment；
4. 测试验证通过且可定位；
5. 所有 included Spec/ADR 已批准；
6. schema/config/contract 已生效；
7. shared capability adoption 和版本影响已验证；
8. 外部合同快照仍有效；
9. 分层 As-Is 已更新；
10. Manifest、traceability 和 supersedes 已更新。

任何关键项失败时输出 `BLOCKED`，保留上一 Canonical Baseline。

## 退出门禁

- Baseline 使用 full SHA；
- branch/environment/status 不含歧义；
- Candidate 与 Canonical 未混淆；
- 每项重要事实有 evidence/confidence；
- As-Is 如实记录偏差；
- 没有 Target-only artifact；
- consumed capability 和 integration 已覆盖；
- Transition 有删除条件；
- Promotion 有测试证据；
- 其他 Skill 的输入包完整。
- 中央 Architecture Context 保持只读，治理候选已单列。
