---
name: explicit-architecture-java-sdd
description: Use when implementing a story card, user story, acceptance criteria, or high-level/overall design in a Java 17 + Spring Boot 3 Explicit Architecture project, especially with Claude Code, Superpowers, SDD, TDD, DDD, Hexagonal Architecture, Clean Architecture, CQRS, Command/Query handlers, ports/adapters, domain events, or layered microservice tests.
---

# 显式架构 Java SDD

在 SDD 流程中使用本 skill，让 Java 17 + Spring Boot 3 代码生成持续遵守显式架构。

## 稳定触发场景

当用户给出 story 卡、用户故事、验收标准、整体概要设计、详细设计、任务卡、issue、Jira 卡，或要求用 Claude Code / Superpowers 实现 Java Spring Boot 功能时，应触发本 skill。

最稳定的用户提示写法：

```text
使用 explicit-architecture-java-sdd，根据这张 story 卡和概要设计实现功能。
```

```text
用 Superpowers + SDD/TDD 实现这个 Java 17 Spring Boot story，遵守显式架构。
```

```text
Claude Code：按 story 卡实现，先拆 SDD 计划，再按 DDD/Hexagonal/CQRS 写测试和代码。
```

## 必须遵守的工作流

1. 先检查仓库级说明：
   - `AGENTS.md`、`CLAUDE.md`、`GEMINI.md` 或等价的 agent 指令。
   - `docs/architecture/**`、`docs/testing**`、ADR，以及已有包结构。
   - 如果仓库声明了权威链，仓库权威链优先于本 skill 和任何模板。
2. 再读取本 skill 的相关 reference：
   - 功能规划或实现：`references/architecture-rules.md`。
   - 测试规划、TDD、验证或评审：`references/testing-rules.md`。
3. 只在真正生成对应代码时加载模板：
   - 生产代码模板：`assets/templates/implementation/`。
   - 测试代码模板：`assets/templates/tests/`。
   - 不要一次性加载整个模板树；只读取 reference 表格中点名的模板文件。
4. 当仓库本地命名和包结构比本 skill 更严格时，遵守仓库本地约定。
5. 规划代码前先识别项目已有共享抽象：
   - Command/Query 接口与 Bus。
   - Aggregate root、DomainEvent、DomainId、异常基类。
   - Outbox、幂等消费者、重试、契约测试约定。
6. 如果输入是 story 卡或概要设计，先抽取：
   - 用户目标、业务能力、验收标准。
   - 涉及的聚合、领域规则、命令路径、查询路径。
   - 外部系统、事件/schema、数据迁移、事务边界。
   - 不明确但会影响架构的假设。
7. 在 Superpowers SDD 计划中，每个任务都必须写明：
   - 触及的精确层和包。
   - 需要的精确测试类型。
   - 属于 command path、query path、adapter、interface、event 还是 schema 工作。
8. TDD 时先在正确层级写测试：
   - 领域规则：先写 domain unit test。
   - Handler 编排：先写 handler unit test。
   - Adapter 行为：先写 adapter integration test。
   - Controller/consumer 映射：先写 slice/unit test。
   - 跨边界契约：先写 contract/schema test。
9. 完成前，对照 `architecture-rules.md` 的分层边界清单和 `testing-rules.md` 的测试清单评审生成代码。

## 不可协商规则

- Domain model 不依赖自身之外的任何东西。
- Domain service 是纯逻辑：不依赖 repository、client、cache、message 组件。
- Application handler 负责编排；Domain 负责决策。
- Infrastructure 实现端口，并容纳框架与技术细节。
- Interfaces 把外部协议翻译成 command/query；不放业务逻辑。
- Command result、query result、外部 response DTO 是三类不同类型。
- Command handler 不得返回 query result 或外部 response DTO。
- Read path 可以绕过 domain model；write path 必须维护领域不变量。
- 跨边界消息是契约；领域事件是本地业务事实。
- 跨服务可靠发布使用项目约定的 outbox/domain-event relay，不得由 handler 直接发布。
- 同服务业务反应属于 driving adapter；技术反应放在对应 infrastructure concern。
- 测试必须分层：大量快速 domain/application 测试在前，少量 adapter/component/contract/e2e 测试在后。

## 代码生成默认值

使用 Java 17 和 Spring Boot 3：

- 适合时使用 `record` 表达 command、query、result、request、response、值对象式 DTO 和事件。
- 如果仓库已有共享 `Command<R>`、`Query<R>`、`CommandHandler<C,R>`、`QueryHandler<Q,R>`，生成代码必须实现它们。
- 使用构造器注入。
- Command/Query handler 使用 `@Service`。
- `@RestController` 只出现在 interfaces 包。
- `@Repository`、`@Component`、`@ConfigurationProperties`、`@Transactional` 只出现在外层。
- 避免字段注入。
- Domain 代码避免框架行为注解。
- 当仓库使用 `interfaces/rest/request` 和 `interfaces/rest/response` 子包时，HTTP request/response DTO 必须放在那里。
- 优先显式 mapper，不要把 persistence entity 泄漏到 application 或 interfaces。

## 模板使用

模板是复制后改造的起点，不是必须原样保留的最终代码。替换所有 `{{PLACEHOLDER}}` 标记，删除不适合最终代码的注释，并按本地包结构调整 import。

不要在 SDD 计划里粘贴所有模板。计划中只引用精确模板路径，并只包含该任务真正需要的最终代码。

## 输出要求

当用户要求规划或实现功能时，输出应包含：

- 按 Domain、Application、Infrastructure、Interfaces、tests 分组的分层文件清单。
- 同时存在写行为和读行为时，明确 command/query 拆分。
- 测试优先顺序和精确测试类名。
- 对任何事务边界或跨边界事件给出明确说明。
- 最终回复前给出简短架构合规检查。

## 评审 Agent

当用户要求架构评审时，如果 `../../agents/explicit-architecture-reviewer.md` 存在，使用它作为评审清单。先报告问题，再给总结，并用 PASS/BLOCKED 给出最终结论。
