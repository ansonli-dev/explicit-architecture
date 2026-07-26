# As-Is 消费契约

本文件只定义其他产物如何识别和消费可部署业务服务 As-Is。Git 取证、分层重建、漂移分析和提升属于 Service As-Is 维护流程的私有方法，不放在共享标准中。公共能力 Owner Current 遵守 `capability-as-is-consumption-contract.md`；中央 Architecture Context 也不能代替任一种 As-Is。

## 目录

- [ASIS-01 固定已验证基线](#asis-01-固定已验证基线)
- [ASIS-02 分离基线与变化集](#asis-02-分离基线与变化集)
- [ASIS-03 分层记录真实架构](#asis-03-分层记录真实架构)
- [ASIS-04 分类事实差异](#asis-04-分类事实差异)
- [ASIS-05 提升已验证现实](#asis-05-提升已验证现实)
- [ASIS-06 单一写入职责](#asis-06-单一写入职责)
- [最小输入包](#最小输入包)

## ASIS-01 固定已验证基线

Canonical As-Is 必须绑定 immutable commit、environment、verification status、verification evidence 和 evidence timestamp。移动分支名不能代替 commit。

本分支 Profile 默认将最后一个通过测试验证的 `test@sha` 视为 Canonical As-Is。先按 `baseline-ref-profile.md` 解析仓库的 verification/development/feature ref；项目有更高权威流程或用户明确提供有效 Profile 时，使用解析后的 ref role。

## ASIS-02 分离基线与变化集

Feature 工作必须区分以下角色；括号内是默认 Profile，实际 ref 使用 `baseline-ref-profile.md` 的解析结果：

```text
Canonical Baseline      = last verified verification-ref commit (test)
Pending Develop Delta   = verified baseline → development-ref commit (develop)
Feature Delta           = actual feature merge-base → feature commit
Test Candidate Delta    = verified baseline → unverified verification/candidate-ref commit
```

所有 ref 固定为 SHA。不能假设 feature 从当前 development-ref HEAD 创建，也不能把 development、feature 或未验证 verification/candidate ref 的内容提前写成 Canonical As-Is。

## ASIS-03 分层记录真实架构

Service As-Is 对证据覆盖范围内的 landscape、实际采用的 shared capabilities、system layers、runtime 和 integrations 如实描述。公共能力的精确 contract/guarantee 回到 Capability As-Is；服务只记录 resolved version、配置、本地扩展和采用偏差。实现偏离 Target 时仍记录实际位置，并通过 deviation 或 confidence 表达，不将其美化成目标设计。

其他 Skill 只摘录与当前 scope 相关的事实，不复制一份新的 As-Is。

## ASIS-04 分类事实差异

使用以下稳定分类：

- `NO_DRIFT`
- `IMPLEMENTATION_DRIFT`
- `AS_IS_STALE`
- `APPROVED_CHANGE`
- `TRANSITION_STATE`
- `ENVIRONMENT_DRIFT`
- `EVIDENCE_GAP`

当前代码存在不自动表示 As-Is 过期。批准依据、验证证据和运行环境不足时，保持 gap 或 candidate。

## ASIS-05 提升已验证现实

只有已部署到声明环境并通过规定验证的 Candidate 才能成为新的 Canonical As-Is。Spec 只提供 intent，代码只提供实现证据，二者均不能单独证明现实已经成立。

Spec 和 Review 产物可以输出 Baseline Gap 或 As-Is Delta Candidate，但不能自行完成提升。

## ASIS-06 单一写入职责

Canonical Service As-Is 只有一个受治理写入流程。其他产物：

- 消费已验证 Baseline；
- 报告 Baseline Gap；
- 提交事实变化候选；
- 不直接修改 Canonical As-Is。

## 最小输入包

Target 生成或审查至少取得：

| Input | Required content |
|---|---|
| Canonical As-Is | baseline ID、Ref Profile、full SHA、environment、status、evidence timestamp |
| Relevant facts | 当前 scope 的分层事实、共享能力采用和外部集成 |
| Pending Develop Delta | baseline → resolved development ref 的相关变化 |
| Feature Delta | actual merge-base → feature；没有实现时可为空 |
| Pending decisions | 已批准但尚未提升的 Spec / ADR |
| Gaps | stale、partial、unknown、environment 或 evidence gap |

Canonical As-Is 缺失、未验证或与绑定 commit 明显不符时，消费方输出 `BASELINE_GAP`，不得在自己的流程中重建后冒充权威输入。
