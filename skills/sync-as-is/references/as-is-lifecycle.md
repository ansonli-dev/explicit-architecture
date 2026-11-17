# As-Is 资产与分支生命周期

As-Is 是经过证据校准的当前架构资产，不是一次扫描报告，也不是 Target Spec。它描述特定环境、特定 commit 已实际成立的结构、行为、能力和合同。本手册负责落实共享 `ASIS-*` 契约，不作为其他 Skill 的直接输入。

默认分支 Profile：

- `develop`：开发主干；
- `feature/*`：从开发主干派生的功能实现分支；
- `test`：稳定测试分支；
- Canonical As-Is：最后一个通过测试验证的 `test` commit。

开始时先按共享 `baseline-ref-profile.md` 解析实际 verification/development/feature ref。下文的 `test/develop/feature` 是默认角色示例；使用自定义 Profile 时以解析后的 role 和 full SHA 替换，但验证、Delta 和提升语义不变。

## 目录

- [固定已验证基线](#固定已验证基线)
- [分离基线与变化集](#分离基线与变化集)
- [分层记录真实架构](#分层记录真实架构)
- [分类事实差异](#分类事实差异)
- [提升已验证现实](#提升已验证现实)
- [单一写入职责](#单一写入职责)
- [As-Is Manifest](#as-is-manifest)
- [Feature 输入视图](#feature-输入视图)

## 固定已验证基线

Canonical As-Is **MUST** 绑定：

- branch；
- immutable commit SHA；
- environment；
- verification status；
- verification evidence；
- evidence timestamp；
- superseded baseline。

不得只记录 `branch = test`。移动分支无法证明文档对应的版本。

若使用独立 As-Is 维护分支，该分支必须从对应的 resolved verified Baseline SHA 派生；维护分支名仍不能替代 Baseline commit。

状态：

- `VERIFIED`：对应 commit 已通过规定的测试验证，可作为下一个 Spec 的 Canonical Baseline；
- `CANDIDATE`：已进入 verification/candidate ref，仍在测试，不得取代最后的 `VERIFIED`；
- `STALE`：已确认与绑定 commit 或环境事实不一致；
- `PARTIAL`：证据范围不完整，必须列出缺口。

开发环境 DC 通过只允许生成 Candidate Delta；不能把 development ref 提升为 Canonical As-Is。

如果未来需要生产环境视图，为每个环境维护独立 Baseline，不把 verification environment 自动当成 production reality。

## 分离基线与变化集

任何 Feature 工作都区分：

```text
Canonical Baseline
  = As-Is @ last VERIFIED verification-ref commit

Pending Develop Delta
  = verified baseline → current development-ref commit

Feature Delta
  = feature merge-base → current feature
```

必要时再区分：

```text
Test Candidate Delta
  = last VERIFIED baseline → current unverified verification/candidate-ref commit
```

Delta 必须绑定起止 SHA 和 merge-base。不要假设 feature 一定从当前 development-ref HEAD 创建，也不要只按分支名计算。

As-Is 保持稳定；development/feature ref 中尚未提升的变化记录为 Delta，不提前回写 Canonical As-Is。

下游设计和审查产物读取：

- Canonical As-Is；
- 相关 Pending Develop Delta；
- 相关 Feature Delta；
- 已批准但尚未提升的 Spec/ADR。

因此，以稳定基线设计不等于忽略开发主干中的新现实。

## 分层记录真实架构

As-Is **MUST** 按证据覆盖范围表达实际结构，而不是把实现美化成 Target。

### Landscape

- system、bounded context、owner；
- 同步/异步依赖；
- authoritative fact；
- environment 和部署边界。

### Consumed Shared Capabilities

- Capability As-Is Baseline ref、owner 和权威 contract 入口；
- 当前服务实际 resolved version 和 environment；
- 生效 configuration、default override 和 extension point；
- 本地 Adapter/Policy/Mapper、采用限制和偏差。

服务 As-Is 不重建公共能力 Owner 的完整 contract、guarantee、兼容矩阵或消费者目录。

### Per System / Bounded Context

- Domain：实际业务语言、模型、规则、状态和行为所在位置；
- Application：实际 use case、orchestration、Port 和 transaction intent；
- Interfaces：实际 driving adapter、protocol 和 validation；
- Infrastructure：实际 persistence、reliability、client 和 technical mechanism；
- Runtime：部署、配置、拓扑、operation 和 observability。

### Integrations

- external/internal contract 和版本；
- 外部流程、authority 和 local meaning；
- unknown、retry、callback、reconciliation 和 compensation。

### Evidence and Drift

- source path/symbol/schema/config/runtime；
- commit/environment；
- confidence；
- known deviation、transition 或 evidence gap；
- linked ADR/spec/finding。

若业务判断实际位于 Controller，As-Is 如实记录该位置和运行路径；可以标记已知偏差，但不得改写成理想 Domain 行为。

只创建有证据的层和视图，不创建空文档以模拟完整。

## 分类事实差异

比较 As-Is、实现、Spec/ADR 和运行环境时，只使用：

- `IMPLEMENTATION_DRIFT`：实现偏离仍有效的批准设计，应修实现；
- `AS_IS_STALE`：实现已按批准设计改变且通过验证，应更新 As-Is；
- `APPROVED_CHANGE`：差异由已批准 Spec/ADR 授权，尚待提升；
- `TRANSITION_STATE`：处于迁移期，必须有 owner、删除条件和期限；
- `ENVIRONMENT_DRIFT`：branch/code 与部署配置或 runtime 不一致；
- `EVIDENCE_GAP`：无法证明哪一方正确；
- `NO_DRIFT`：事实、基线和批准决定一致。

当前代码存在不能自动判定 `AS_IS_STALE`。先检查批准的 Spec/ADR、测试和部署证据，避免用未经批准的实现覆盖基线。

## 提升已验证现实

只有 `POST_DELIVERY_PROMOTE` 可以把 Target 变化提升为 Canonical As-Is。

顺序：

1. 读取上一 resolved `VERIFIED` Baseline SHA；
2. 固定 verification/candidate ref 上的新 Candidate SHA；
3. 确认 feature/development ref 累积变化及批准 Spec/ADR；
4. 核验 code、schema、configuration、contract 和 runtime evidence；
5. 确认测试验证已通过；
6. 更新受影响的分层 As-Is；
7. 记录服务 added/changed/removed capability adoption、contract usage 和 integration；
8. 关闭已完成 Transition，保留未完成 Transition；
9. 将 Candidate 标记为 `VERIFIED` 并链接 superseded baseline。

如果测试失败、环境未部署、Schema 未生效或关键合同无法验证，不得提升。

Spec 提供 intent，代码和运行证据证明 reality；任一方都不能单独完成提升。

## 单一写入职责

Canonical Service As-Is 只有一个受治理写入流程。公共能力 Owner 的 Capability As-Is 是独立事实资产。

- 其他产物可以消费 Baseline；
- 可以输出 Baseline Gap 或 As-Is Delta Candidate；
- 不直接修改 Canonical Service As-Is；
- implementation/plan 不直接修改 Canonical As-Is；
- approved Spec/ADR 可登记为 Pending Decision，验证后由受治理维护流程提升。

用户明确要求修改 As-Is 时，仍必须执行对应 mode 和证据门禁。

## As-Is Manifest

```yaml
baseline_id: "asis-{{VERIFICATION_REF}}-{{SHORT_SHA}}"
ref_profile: "{{PROFILE_ID}}"
verification_ref: "{{VERIFICATION_REF}}"
commit: "{{FULL_SHA}}"
environment: "{{ENVIRONMENT}}"
status: "VERIFIED"
verified_at: "{{TIMESTAMP}}"
verification_evidence:
  - "{{EVIDENCE}}"
supersedes: "{{PREVIOUS_BASELINE_ID}}"
evidence_scope:
  - "{{SCOPE}}"
known_gaps:
  - "{{GAP}}"
```

Manifest 表达的是逻辑字段，不绑定实际序列化格式。若项目已有 metadata 规范，映射到其格式。

## Feature 输入视图

每份 Feature Spec 记录：

| Field | Meaning |
|---|---|
| Ref Profile | profile ID、source、verification/development/feature ref |
| As-Is Baseline | `{verification_ref}@{verified_sha}` |
| Baseline status | 必须为 `VERIFIED`，否则说明例外 |
| Development ref | `{development_ref}@{sha}` |
| Pending Develop Delta | baseline → development ref 的相关变化 |
| Feature ref | `feature@{sha}`，尚无实现时可为空 |
| Feature merge-base | 实际 merge-base |
| Pending decisions | 已批准但尚未提升的 Spec/ADR |
| Evidence timestamp | 读取基线与分支事实的时间 |

Spec 不复制完整 As-Is，只引用 Baseline 并摘录与当前需求有关的事实和 Delta。
