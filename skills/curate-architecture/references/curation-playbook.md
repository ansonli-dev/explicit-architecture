# Architecture Context 整理手册

## 目录

- [安全盘点](#安全盘点)
- [分类顺序](#分类顺序)
- [充分性分析](#充分性分析)
- [冲突与重复](#冲突与重复)
- [重组策略](#重组策略)
- [写入门禁](#写入门禁)
- [验证](#验证)

## 安全盘点

先读取 repository instructions、Git 状态、现有 index、目录和内部链接。记录当前 commit；不要在未知 dirty worktree 中批量移动文件。

优先：

1. 读取 index 和 canonical landing page；
2. 枚举受治理文档类型；
3. 按 status/authority/scope 抽样正文；
4. 搜索未索引文件、重复标题、旧路径和 broken links；
5. 按 Spec 风险补读；
6. 只读核验服务、能力和合同 owner。

未经用户要求，`INVENTORY/GAP_ANALYSIS` 不修改文件。

## 分类顺序

对每份文档依次判断：

1. 它表达 Current、Target、mandatory rule、candidate solution、decision history 还是 navigation？
2. 自然 owner 是否在中央仓库？
3. 谁可以改变它？
4. 哪些需求或服务触发适用？
5. 不读取它会让 Spec 丢失什么依据？
6. 是否存在更高 authority 或 superseding document？
7. 是否混入例子、实现细节或过期 Current？

无法稳定分类时标记 `UNCLASSIFIED`，不要仅按目录名决定。

## 充分性分析

从典型 Feature 类型反向测试：

- 新增同步 API；
- 新增 event 或 consumer；
- 引入多语言/locale；
- 新增外部系统集成；
- 扩展 shared capability；
- 跨服务状态推进和补偿；
- 数据存储或 schema 演进；
- 安全、审计和隐私变化。

对每类问题检查 Agent 能否通过 index 发现：

```text
Relevant services and owners
  → Boundaries and mandatory Standards
  → Candidate Patterns and shared capabilities
  → Contracts and decision history
  → Natural owner evidence
```

若必须依靠猜测、全仓扫描或熟悉团队历史才能找到，视为上下文缺口。

## 冲突与重复

区分：

- 两个 `ACTIVE + MANDATORY` 文档给出不同规则：`CONFLICTING`；
- 新文档已替代旧文档但 index 未更新：`STALE / SUPERSEDES_GAP`；
- Standard 与 Pattern 重复同一 rule：`MIXED_AUTHORITY`；
- 中央摘要与 owner contract 不同：`CONFLICTING / OVERDETAILED`；
- 相同标题但 scope 不同：先澄清 applicability，不自动合并。

没有批准依据时只报告，不自行决定 canonical。

## 重组策略

按最小改动顺序：

1. 补 metadata/index；
2. 修 owner、related 和 natural owner link；
3. 澄清 status、authority、scope 和 applicability；
4. 标记 supersedes/deprecated；
5. 拆分 mixed authority；
6. 合并真正重复的 canonical expression；
7. 最后才移动目录和归档。

移动时保留 redirect/link 或更新所有引用。不要通过大规模改名制造无业务价值的 diff。

## 写入门禁

写入前确认：

- 用户明确要求维护；
- exact repository/root/path 已确认；
- 当前 branch/worktree 状态已知；
- canonical authority 与 owner 已确认；
- 新内容有稳定 ID、type、status、authority、scope 和 applicability；
- move/merge/deprecate 有 supersedes 和 consumer impact；
- 不覆盖 Service/Capability As-Is、合同 owner 或 Feature Spec；
- 删除具有明确批准和恢复方式。

分析结论不能自动转成 `ACTIVE + MANDATORY`。

## 验证

完成后检查：

- index 中的 path 存在；
- active 文档有 owner、version 和 last_reviewed；
- supersedes 关系无环且双向可追踪；
- mandatory rule 只有一个 canonical 表达；
- Pattern 明确 non-guarantees 和适用边界；
- Catalog 指向自然 owner；
- Mermaid 和内部链接可解析；
- 没有模板 placeholder；
- 没有为目录完整创建空文件；
- Feature Skill 可从 index 用触发条件找到适用上下文。
