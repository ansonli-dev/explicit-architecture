# 规范索引

本文件提供稳定规则 ID。引用规则时必须复制这里的精确 ID 和名称，不得缩写、重新编号或为具体方案编造 `CTX/ASIS/CASIS/DISC/DR/MECH/GEN/TECH` ID。

## 目录

- [引用规则](#引用规则)
- [CTX — Central Architecture Context](#ctx--central-architecture-context)
- [ASIS — Service Current Architecture Asset](#asis--service-current-architecture-asset)
- [CASIS — Capability Current Architecture Asset](#casis--capability-current-architecture-asset)
- [DISC — Discovery Baselines](#disc--discovery-baselines)
- [DR — Decision Reasoning](#dr--decision-reasoning)
- [MECH — Mechanism Families](#mech--mechanism-families)
- [GEN — Capability Generalization](#gen--capability-generalization)
- [TECH — Technology Discovery and Selection](#tech--technology-discovery-and-selection)

## 引用规则

- 使用零补齐形式：`DR-01`，不能写 `DR-1`。
- `CTX-*` 只表示中央 Architecture Context 的组织、消费和治理义务。
- `ASIS-*` 只表示业务服务 Current、分支基线、漂移和提升义务。
- `CASIS-*` 只表示公共能力 Current、resolved capability current-ref 基线、兼容漂移和更新职责；默认 current ref 为 `develop`。
- `DISC-*` 只表示设计前 As-Is 与外部证据发现义务。
- `DR-*` 只表示推理义务。
- `MECH-*` 只表示机制族，不表示具体产品或某个 feature 的方案。
- `GEN-*` 只表示公共能力/Shared Kernel 泛化判定，避免与 CAP theorem 混淆。
- `TECH-*` 只表示技术能力发现与实现选择义务。
- 具体 feature 决定使用 `DEC-{FEATURE}-{NN}`，例如 `DEC-RATE-LIMIT-01`。
- 没有匹配规则时引用章节标题并说明缺口，不得创造新的标准 ID。
- Finding 至少引用一条精确标准；不要求为了引用而列出无关 ID。

## CTX — Central Architecture Context

| ID | Official name | Obligation |
|---|---|---|
| `CTX-01` | 分离治理上下文与服务现实 | 中央仓库不复制 Service/Capability As-Is |
| `CTX-02` | 建立可检索的适用性索引 | 先按 type、scope 和 trigger 发现资料 |
| `CTX-03` | 提供最小系统图景 | 能发现服务、owner、边界、关系和资料入口 |
| `CTX-04` | 区分 Architecture Standard Pattern 与 ADR | 不混淆强制约束、候选方案和决定历史 |
| `CTX-05` | 链接自然所有者的事实 | Current、contract 和 capability 回到自然 owner |
| `CTX-06` | Feature 流程只读 | Feature Skill 只输出治理候选 |
| `CTX-07` | 固定版本与适用范围 | 记录 commit/version、status、scope 和 applicability |
| `CTX-08` | 识别并治理上下文缺口 | 按 Spec 影响识别缺失、冲突、过期和不可发现内容 |

## ASIS — Service Current Architecture Asset

| ID | Official name | Obligation |
|---|---|---|
| `ASIS-01` | 固定已验证基线 | Canonical As-Is 绑定 resolved verification ref 上的 verified SHA 与环境证据；默认 ref 为 `test` |
| `ASIS-02` | 分离基线与变化集 | 区分 baseline、development delta、feature delta 和 candidate |
| `ASIS-03` | 分层记录真实架构 | 按实际证据表达 landscape、layers、capabilities 和 integrations |
| `ASIS-04` | 分类事实差异 | 区分 implementation/as-is/environment drift 与 approved transition |
| `ASIS-05` | 提升已验证现实 | 只有测试通过的实现可成为新 Canonical As-Is |
| `ASIS-06` | 单一写入职责 | Canonical Service As-Is 只有一个受治理写入流程 |

## CASIS — Capability Current Architecture Asset

| ID | Official name | Obligation |
|---|---|---|
| `CASIS-01` | 回到能力自然所有者 | 精确能力 Current 由能力 Owner 仓库拥有 |
| `CASIS-02` | 固定能力稳定基线 | Baseline 绑定 resolved capability current ref 的 full SHA 和证据范围；默认 ref 为 `develop` |
| `CASIS-03` | 只读取能力 Current Ref | 其他 ref 和 working tree 不进入 Capability Current |
| `CASIS-04` | 按能力契约记录真实 Current | 记录 contract、guarantee、defaults、extension、兼容和迁移 |
| `CASIS-05` | 分类能力事实差异 | 区分 As-Is、implementation、contract 和 evidence gap |
| `CASIS-06` | 根据能力 Current Ref 更新基线 | 只用同一 resolved current-ref SHA 的事实更新 Current |
| `CASIS-07` | 保持单一事实所有者 | Canonical Capability As-Is 只有一个受治理写入流程 |

## DISC — Discovery Baselines

| ID | Official name | Obligation |
|---|---|---|
| `DISC-01` | 消费共享能力基线 | 从能力 Owner Baseline 和服务采用事实确认 contract、版本与 fit |
| `DISC-02` | 裁决能力适配方式 | 按 reuse/configure/compose/extend/replace/new 顺序判断 |
| `DISC-03` | 使用有效外部合同 | 以当前有效 contract、version 和协议语义为设计依据 |
| `DISC-04` | 使用真实外部流程 | 以 authority、状态、未知结果与恢复流程为设计依据 |
| `DISC-05` | 执行设计就绪门禁 | 缺失依据会改变模型、能力归属或合同则阻塞 |

## DR — Decision Reasoning

| ID | Official name | Obligation |
|---|---|---|
| `DR-01` | 恢复真实目标 | 从业务结果和不变量开始 |
| `DR-02` | 分类设计力 | 识别适用 forces，不按案例匹配 |
| `DR-03` | 确定权威、范围和时点 | 机制范围不得小于约束范围 |
| `DR-04` | 建立失败模型 | prevent/detect/recover/tolerate/exclude |
| `DR-05` | 分配语义、决策和机制 | 分离 semantic/decision/mechanism/operational owner |
| `DR-06` | 按保障选择机制 | 从 guarantee 反推 mechanism family |
| `DR-07` | 标准路径测试 | 拒绝隐藏顺序和框架偶然行为 |
| `DR-08` | 反事实测试 | 用新入口、并发、崩溃、重复、版本攻击方案 |
| `DR-09` | 最小性测试 | natural owner 和 deletion test |
| `DR-10` | 公共能力透镜 | 对非领域机制执行 GEN 判断 |
| `DR-11` | 演进与可逆性 | compatibility、rollback、removal |
| `DR-12` | 证据闭环 | 验证真实 guarantee、scope 和 point |

## MECH — Mechanism Families

| ID | Official name | Primary guarantee |
|---|---|---|
| `MECH-01` | 声明式不变量 | 阻止权威存储中的非法状态 |
| `MECH-02` | 原子条件变更 | 原子判断并变更 |
| `MECH-03` | 冲突检测 | 检测基于旧快照的覆盖 |
| `MECH-04` | 串行化 | 在 key/partition 范围内互斥或排序 |
| `MECH-05` | 本地事务 | 原子提交同事务资源 |
| `MECH-06` | 持久化意图 | 业务状态与待执行意图共同持久 |
| `MECH-07` | 幂等效果 | 重复执行保持稳定业务效果 |
| `MECH-08` | 重试与续跑 | 暂时失败后的持久恢复 |
| `MECH-09` | 对账与修复 | 发现并收敛跨边界漂移 |
| `MECH-10` | 补偿 | 业务上抵销不可回滚效果 |
| `MECH-11` | 派生表示 | 外层约束、读取或协议表示 |

## GEN — Capability Generalization

| ID | Official name | Question |
|---|---|---|
| `GEN-01` | 去业务化 | 移除业务名词后机制是否仍完整？ |
| `GEN-02` | 机制身份 | guarantee、状态、恢复和边界是否稳定？ |
| `GEN-03` | 稳定核心与变化轴 | 公共核心与本地策略能否分开？ |
| `GEN-04` | 生命周期与状态所有权 | 机制能否独立治理？ |
| `GEN-05` | 第一个消费者 | 是否无需等待代码重复即可公共化？ |
| `GEN-06` | 升级现有能力 | 应扩展、组合还是新建机制？ |
| `GEN-07` | API 与治理 | 兼容、运维和弃用是否完整？ |
| `GEN-08` | 不泛化条件 | 是否应保留本地？ |
| `GEN-09` | Shared Kernel 门禁 | 是否真正共同拥有领域语义？ |

## TECH — Technology Discovery and Selection

| ID | Official name | Obligation |
|---|---|---|
| `TECH-01` | 先确定架构意图 | 先选语义和机制族，再看技术语法 |
| `TECH-02` | 消费有效技术栈 | 从相关 Canonical Current 取得精确 stack、版本和环境证据 |
| `TECH-03` | 使用 Technology Capability Profile | 只摘录当前决定依赖的能力 |
| `TECH-04` | 核验版本能力 | 使用精确版本的一手证据 |
| `TECH-05` | 核验集成层限制 | 检查 compiler/framework/data-access/driver/tooling |
| `TECH-06` | 核验环境交集 | 保证 production 等有效环境可用 |
| `TECH-07` | 选择最优实现 | 在可用候选中按架构质量裁决 |
| `TECH-08` | 记录证据与失效条件 | 记录 fallback、演进和重新评估点 |
