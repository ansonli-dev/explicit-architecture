# 中央 Architecture Context 内容模型

中央仓库的目标是让 Feature Skill 用最少读取获得足够的系统级 Target 上下文。它不集中保存服务实现事实。

## 目录

- [默认结构](#默认结构)
- [入口索引](#入口索引)
- [Landscape](#landscape)
- [Architecture](#architecture)
- [Cross-service Flows](#cross-service-flows)
- [Standards](#standards)
- [Patterns](#patterns)
- [Capability Catalog](#capability-catalog)
- [Integration Catalog](#integration-catalog)
- [Decisions](#decisions)
- [Glossary](#glossary)
- [不应进入中央仓库的内容](#不应进入中央仓库的内容)

## 默认结构

```text
architecture-context/
├── architecture-index.yaml
├── landscape/
│   ├── system-context.md
│   └── service-catalog.md
├── architecture/
│   └── {concern}.md
├── flows/
│   └── {cross-service-flow}.md
├── standards/
│   └── {concern}/{standard}.md
├── patterns/
│   └── {concern}/{pattern}.md
├── capabilities/
│   └── capability-catalog.md
├── integrations/
│   └── integration-catalog.md
├── decisions/
│   └── decision-index.md
└── glossary/
    └── glossary.md
```

沿用已有清晰结构优先于迁移。该结构只在 bootstrap 或现状无法治理时使用。

## 入口索引

`architecture-index.yaml` 是 Agent 的首个入口，负责：

- 按 type、scope、service、protocol、capability、integration、environment 和 trigger 找文档；
- 区分 mandatory、advisory 和 informational；
- 暴露 owner、status、version、supersedes 和 related；
- 避免递归加载整个仓库；
- 发现未索引、重复 active authority 和过期资料。

Index 不复制正文规则。正文仍是 canonical content。

## Landscape

### System Context

回答：

- 系统服务哪些业务能力和参与者；
- system/bounded-context/platform 的边界；
- 高层同步、异步和外部关系；
- 系统级 owner 和事实所有权；
- 哪些位置提供详细 Service/Capability As-Is、合同和运行证据。

使用 Context/Container 风格图或普通 Mermaid，避免包含每个类、表和 Topic。

### Service Catalog

每个服务只记录：

- stable ID、name、purpose 和 bounded context；
- owner/team；
- repository 和 local As-Is link；
- authoritative facts；
- provided/consumed contracts 的发现链接；
-主要 upstream/downstream；
- lifecycle/status。

不复制服务分层模型、技术版本或完整 API。

## Architecture

记录跨项目或跨服务的长期 Target：

- problem 和 scope；
- boundaries、allowed dependencies 和 ownership；
- consistency、security、reliability 或 lifecycle constraints；
- mandatory 与 variable points；
- 适用和不适用范围；
- approved exceptions；
- related Standards、Patterns、ADRs 和验证方式。

Architecture 文档不规定某一技术版本的偶然语法，除非这本身是批准的系统约束。

## Cross-service Flows

只维护会改变服务边界、事实所有权、状态推进、合同或恢复策略的稳定流程：

- business outcome、trigger 和 participants；
- authoritative facts；
- normal、rejected、cancelled、unknown、compensation 和 reconciliation；
- local state advancement；
- interaction sequence；
- contract/process owner links；
- related Architecture、Standards 和 ADR。

单 Feature 的临时流程留在 Spec。已经运行的低层细节回到服务 As-Is。

## Standards

Standard 必须包含：

- normative rules，明确 `MUST/SHOULD/MAY`；
- applicability 和 exclusions；
- rationale；
- conformance criteria；
- verification；
- exception process；
- compatibility/migration；
- owner、version、status、supersedes；
- examples 与 normative text 明确分开。

适合的主题包括：

- API/HTTP response、errors、versioning；
- event/schema、delivery 和 compatibility；
- data ownership、migration 和 retention；
- authentication、authorization 和 privacy；
- observability、correlation 和 audit；
- localization、多语言语义与资源治理；
- configuration、secrets 和 environment；
- testing、contract verification 和 delivery governance。

不要因为已有某个实现就自动把它提升为 Standard。

## Patterns

Pattern 是待裁决候选，必须包含：

- problem；
- design forces；
- required guarantee 和 non-guarantees；
- applicability 和 contraindications；
- stable mechanism；
- technology-sensitive variants；
- trade-offs、failure modes 和 operational cost；
- compatibility/evolution；
- verification；
- related Shared Capability 和 Standards。

例如多语言方案可以拆成：

- 跨系统语言/locale 语义和 fallback Standard；
- 文案、资源、缓存、翻译流程等 Pattern；
- 已有国际化平台能力的 Catalog 与 Capability As-Is 链接；
- 各服务实际采用版本留在本地 As-Is。

## Capability Catalog

用于发现已有公共能力，不成为 Capability As-Is 或 contract 的第二事实源。每项记录：

- stable capability name 和 purpose；
- owner/repository；
- canonical Capability As-Is/contract/version link；
- guarantee 摘要；
- extension points；
- consumer discovery；
- limitations；
- related Standards/Patterns。

详细 contract、state/lifecycle、resolved Capability Current Ref 中的有效模块版本和兼容信息从 Capability As-Is 获取；服务实际 resolved version、配置和采用偏差从 Service As-Is 获取。

## Integration Catalog

每个外部系统或组织边界记录：

- system、business owner、technical owner；
- contract/schema registry link；
- process authority 和流程资料；
- environments；
- authentication/security owner；
- callback/query/cancel/compensation 能力入口；
- change/deprecation policy；
- consuming services discovery；
- known gap。

不复制供应商完整文档，也不根据本地 client 反推外部合同。

## Decisions

Decision Index 提供：

- ADR ID、title、status 和 scope；
- decided/superseded date；
- supersedes/superseded-by；
- affected Architecture/Standards/Patterns/services；
- canonical path。

ADR 正文保持历史稳定。改变决定时创建新 ADR 并更新关系，不覆写旧 context。

## Glossary

只收录跨系统或跨团队可能产生建模歧义的术语：

- canonical term；
- definition；
- owner/bounded context；
- aliases 和 prohibited meanings；
- related facts/states/contracts。

服务内部实现术语留在服务文档。

## 不应进入中央仓库的内容

- 服务代码结构和完整 Service As-Is；
- 公共能力详细 contract、版本、兼容和 Capability As-Is；
- 每个 endpoint、table、field 和 configuration 的镜像；
- 未批准 Feature Spec/Plan；
- 无 owner 的最佳实践摘抄；
- 绑定单一版本但未说明适用性的永久规则；
- 与 Standard 重复的 Pattern；
- 与自然 owner 冲突的能力或合同副本；
- 只为填满目录创建的空文档。
