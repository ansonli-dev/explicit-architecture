# 公共能力 As-Is 生命周期

Capability As-Is 描述固定 `{capability-current-ref}@sha` 当前提供的公共能力。默认 Current ref 为 `develop`，也可由 Baseline Ref Profile 显式映射。该 ref 被视为稳定且已发布状态，因此不建立独立 release、test 或 artifact promotion 模型。

## Baseline

```text
Canonical Capability Baseline = {capability-current-ref}@full-sha
Default capability-current-ref = develop
```

Manifest 至少包含 repository、Ref Profile、resolved ref、full SHA、status、evidence timestamp、scope 和 supersedes。仓库声明统一 SDK/module version 时一并记录，但版本不能代替 commit。

## 状态

- `STABLE`：与固定 current-ref commit 一致，证据充分；
- `PARTIAL`：只覆盖部分 module/capability，并明确缺口；
- `STALE`：与绑定 current-ref commit 不一致；
- `BLOCKED`：selected current ref 或关键证据不可取得。

## Drift

| Classification | Meaning |
|---|---|
| `NO_DRIFT` | 文档与固定 current-ref 事实一致 |
| `CAPABILITY_AS_IS_STALE` | fixed current ref 中的稳定事实尚未反映到文档 |
| `IMPLEMENTATION_DRIFT` | fixed current ref 偏离仍有效的批准 contract/standard |
| `CONTRACT_GAP` | public contract、guarantee、defaults 或 extension 依据不足 |
| `EVIDENCE_GAP` | 无法证明文档或实现事实 |

## 更新

更新顺序：

1. 解析 Ref Profile 并固定 `{capability-current-ref}@full-sha`；
2. 读取上一 Baseline；
3. 对比 module、capability、contract、defaults、extension 和 limitations；
4. 验证适用的 tests 和 consumer evidence；
5. 更新受影响文档与 traceability；
6. 新 Baseline 标记 `STABLE` 或 `PARTIAL` 并链接 superseded Baseline。

其他 ref、working tree 和未合入变化不进入此次 Current。
