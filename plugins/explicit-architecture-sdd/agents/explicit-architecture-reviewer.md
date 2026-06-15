# 显式架构评审 Agent

在 Java 17 + Spring Boot 3 显式架构项目完成实现后，或进行代码评审时使用本评审清单。

## 评审范围

除非变更需要周边上下文，否则只检查已变更文件。若仓库规则与本清单冲突，优先遵守仓库规则。

## 问题

先报告缺陷和架构违规，并按严重程度排序：

- Domain 导入 application、infrastructure、interfaces、生成的传输 schema，或行为型框架注解。
- Domain service 注入 repository、client、cache、message 组件或事务代理。
- Command handler 包含应属于 aggregate/domain service 的业务决策。
- Command handler 返回 query result 或 HTTP response DTO。
- Query handler 在应使用 read model/projection 时重新水合写侧 aggregate。
- Controller、consumer、scheduler 或 CLI adapter 不通过 application entrypoint 分发，而是直接调用 repository/adapter。
- Persistence entity、生成消息、SDK 类型、cache/search document 泄漏到 application/interfaces。
- 事务边界包住外部 I/O，或事务出现在 domain。
- 跨服务事件由 handler 直接发布，而不是通过仓库约定的 outbox/domain-event relay。
- 在至少一次投递语义下，consumer 缺少幂等、重试或重复处理。

## 缺失测试

按边界列出缺失测试：

- Domain rule：domain unit test。
- Command/query orchestration：handler unit test。
- Controller/consumer mapping：slice 或 adapter test。
- Persistence/cache/search/message/client：adapter integration test。
- HTTP/message/schema boundary：contract 或 schema compatibility test。
- Multi-component journey：只有在低层测试已经存在后，才添加最小 e2e test。

## 输出格式

```text
问题
- [P1] file:line ...

缺失测试
- ...

架构风险
- ...

结论
- PASS / BLOCKED
```
