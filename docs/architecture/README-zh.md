# 架构决策记录

本目录收录在线书店 Demo 项目的 ADR（Architecture Decision Records，架构决策记录）。

每条 ADR 记录一项重要的架构决策：背景、备选方案、最终决策以及正负两面的影响。

## 格式

ADR 遵循 [Michael Nygard 模板](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)：

- **状态（Status）**：Proposed / Accepted / Deprecated / Superseded by ADR-XXX
- **背景（Context）**：为何需要做出此决策
- **决策（Decision）**：最终决定了什么
- **影响（Consequences）**：权衡取舍，包含正面和负面影响

## 索引

| ADR | 标题 | 状态 |
|---|---|---|
| [ADR-001](ADR-001-explicit-architecture-over-layered.md) | 采用显式架构替代传统分层架构 | Accepted |
| [ADR-002](ADR-002-cqrs-scope-order-service.md) | CQRS 仅应用于 order 服务 | Accepted |
| [ADR-003](ADR-003-event-schema-ownership.md) | 将事件 Schema 集中管理于 shared-events 模块 | Accepted |
| [ADR-004](ADR-004-istio-mesh.md) | 使用 Istio Service Mesh 替代应用层弹性库 | Accepted |
| [ADR-005](ADR-005-outbox-pattern.md) | 使用 Outbox Pattern 保证领域事件可靠投递 | Accepted |
| [ADR-006](ADR-006-database-per-service.md) | 每服务独立数据库，禁止共享表 | Accepted |
| [ADR-007](ADR-007-java21-virtual-threads.md) | 使用 Java 21 虚拟线程与现代语言特性 | Accepted |
| [ADR-009](ADR-009-kafka-consumer-idempotency-retry.md) | Kafka 消费者幂等性与数据库支撑的重试机制 | Accepted |
| [ADR-010](ADR-010-opentelemetry-observability.md) | 通过 Kubernetes Operator 统一接入 OpenTelemetry 可观测性 | Accepted |
| [ADR-011](ADR-011-swaggerhub-pactflow-bdct.md) | SwaggerHub + PactFlow 双向契约测试 | Accepted |

## 如何新增一条 ADR

1. 将下方模板复制到新文件，命名为 `ADR-{NNN}-{short-title}.md`
2. 填写所有小节
3. 在上方索引表中添加一行
4. 若新 ADR 取代了某条已有 ADR，将旧 ADR 的状态更新为 `Superseded by ADR-{NNN}`

```markdown
# ADR-NNN: Title

- **Status**: Proposed
- **Date**: YYYY-MM-DD
- **Deciders**:

---

## Context

## Decision

## Consequences

### Positive

### Negative
```
