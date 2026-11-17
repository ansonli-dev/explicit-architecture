# Baseline Ref Profile

本文件定义 Skill 套件如何把稳定 Current、开发变化、验证候选和 Feature 变化映射到仓库实际使用的 Git ref。它只配置 ref 角色，不改变 `ASIS-* / CASIS-*` 对 immutable SHA、证据和提升门禁的要求。

## 默认 Profile

未取得其他受治理配置时，使用：

```text
Service:
  verification_ref = test
  development_ref  = develop
  feature_ref       = user-provided feature ref
  Canonical Current = last VERIFIED commit on test

Capability:
  current_ref       = develop
  Canonical Current = fixed develop@full-sha
```

默认 Profile 保留本套件既有的 `test / develop / feature` 语义。分支名只是角色默认值，不是 Current 证据；所有消费和输出仍固定 full SHA。

## 可配置字段

```yaml
profile_id: "{{PROFILE_ID}}"
source: "{{USER_EXPLICIT|REPOSITORY_CANONICAL|SUITE_DEFAULT}}"
service:
  verification_ref: "{{REF}}"
  development_ref: "{{REF}}"
  feature_ref: "{{REF_OR_NA}}"
  candidate_ref: "{{REF_OR_NA}}"
  environment: "{{ENVIRONMENT}}"
capability:
  current_ref: "{{REF}}"
resolved_at: "{{TIMESTAMP}}"
```

只声明当前任务实际使用的字段。Service As-Is 任务不需要伪造 Capability ref；Capability As-Is 任务也不需要创建 Service 分支角色。

## 解析顺序

1. 使用用户对本次任务明确给出且不违反仓库权限/治理规则的 Profile；
2. 否则使用仓库 canonical workflow 明确声明的 Profile；
3. 否则使用本文件的默认 Profile。

若多个来源冲突且无法根据 authority 消解，输出相应 `BASELINE_GAP` 或 `CAPABILITY_BASELINE_GAP`，不要静默选择。

## 固定与记录

解析 Profile 后：

1. 记录 `profile_id`、source 和所有实际使用的 ref role；
2. 使用只读 Git 命令将每个 ref 固定为 full SHA；
3. 记录 local/remote 解析来源，不自动 fetch；
4. Service Canonical Current 仍必须定位最后一个满足验证门禁的 commit，不能把 verification ref HEAD 自动视为已验证；
5. Capability Current 只从固定后的 `current_ref@sha` 读取，不用其他 ref 补充；
6. 输出和模板使用解析后的 ref 名称，不把默认 `test/develop` 写成普遍事实。

不得为了应用 Profile checkout、switch、merge、rebase 或 reset 用户分支。需要完整目录或构建时使用基于 fixed SHA 的独立 worktree。

## 消费要求

- `sync-as-is` 负责解析并记录 Service Profile；
- `sync-capability` 负责解析并记录 Capability Profile；
- `to-spec`、`review-spec` 和 `review-codebase` 消费已记录的 Profile 与 fixed SHA；
- Profile 改变只改变 ref 映射，不自动提升、重建或改写任一 Canonical As-Is；
- 缺失 Profile 但默认 ref 可解析时继续使用默认 Profile；默认 ref 也不可解析时才形成 Baseline Gap。
