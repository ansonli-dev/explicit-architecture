# 架构规则

在 Java 17 + Spring Boot 3 显式架构项目中生成代码时使用这些规则。本文件是决策指导；只有在生成对应代码时才加载 `assets/templates/implementation/` 下的模板。

## 包分区

优先使用仓库已有包名。如果从零创建，默认采用：

```text
com.example.{context}/
├── domain/
│   ├── model/
│   ├── ports/
│   ├── event/
│   └── service/
├── application/
│   ├── command/{feature}/
│   ├── query/{feature}/
│   └── port/outbound/
├── infrastructure/
│   ├── repository/
│   │   ├── jpa/
│   │   └── elasticsearch/
│   ├── client/
│   ├── messaging/
│   │   └── outbox/
│   ├── cache/
│   └── config/
└── interfaces/
    ├── rest/
    ├── messaging/consumer/
    ├── event/
    └── job/
```

只创建包含真实代码的包。不要创建空占位包。

## 实现模板路由

生成生产代码时，只读取需要的模板：

| 需求 | 模板 |
|---|---|
| 带业务不变量的聚合根 | `assets/templates/implementation/aggregate.java` |
| 值对象 | `assets/templates/implementation/value-object.java` |
| 领域事件 | `assets/templates/implementation/domain-event.java` |
| 写侧 repository port | `assets/templates/implementation/repository-port.java` |
| Command record | `assets/templates/implementation/command.java` |
| Command result record | `assets/templates/implementation/command-result.java` |
| Command handler | `assets/templates/implementation/command-handler.java` |
| Query record | `assets/templates/implementation/query.java` |
| Query result record | `assets/templates/implementation/query-result.java` |
| Query handler | `assets/templates/implementation/query-handler.java` |
| Persistence adapter | `assets/templates/implementation/persistence-adapter.java` |
| REST controller | `assets/templates/implementation/rest-controller.java` |
| REST request DTO | `assets/templates/implementation/rest-request.java` |
| REST response DTO | `assets/templates/implementation/rest-response.java` |
| Outbox mapper | `assets/templates/implementation/outbox-mapper.java` |

模板使用 `{{PLACEHOLDER}}` 标记。替换每个标记，并删除对最终代码无帮助的注释。

## Domain 规则

放在 Domain：

- 聚合、实体、值对象、枚举、领域异常。
- 作为本地事实的领域事件。
- 使用领域语言表达的写侧 repository port。
- 纯跨聚合逻辑的 domain service。

规则：

- 不导入 `jakarta.persistence`、Spring Data、web、messaging、cache 或 client 类型。
- 不使用 `@Entity`、`@Table`、JPA 关系注解、`@Transactional` 或框架代理行为。
- Domain service 不注入 port 或 adapter；所有所需数据都由方法参数传入。
- 业务 guard 放在 aggregate/domain method 中，不放在 handler 中。
- Domain event 不导入跨边界 schema 或生成的传输类。
- 当仓库使用该约定时，repository port 优先使用表达领域集合语义的命名，例如 `{Aggregate}Persistence`。

## Application 规则

放在 Application：

- `Command` record、command result record、command handler。
- `Query` record、query result record、query handler。
- 外部 client、read model、search、cache、notification、file storage 等 outbound port。

Command handler 流程：

1. 通过 port 加载数据。
2. 调用 aggregate/domain service。
3. 通过写侧 port 保存 aggregate。
4. 从内存中的领域状态组装并返回 command result。

Query handler 流程：

1. 接收 query。
2. 如果项目有授权/作用域规则，先应用它。
3. 通过 read model/search/projection port 读取。
4. 返回 query result。

规则：

- 不导入 persistence 注解、SDK client、messaging library、web 注解、cache API 或原始技术类型。
- Handler 上可以使用 `@Service`。
- 如果仓库已有共享 handler 接口，必须实现；不要自行发明 per-use-case inbound port，除非仓库已经这么做。
- 不使用字段注入。
- Command handler 不返回 query result record 或外部 response DTO。
- Query handler 不重新水合 domain aggregate，除非仓库架构明确要求。
- 当项目使用 outbox 或 domain-event relay 时，handler 不直接发布事件。

## Infrastructure 规则

放在 Infrastructure：

- Persistence entity 和 repository。
- Persistence model 与 domain model 的 mapper。
- Application/domain port 的 adapter 实现。
- 外部 client、messaging adapter、cache adapter、file adapter、configuration class。
- 将本地 domain event 翻译为 integration contract 的 outbox mapper。

规则：

- 框架和技术细节放在这里。
- Persistence entity 不得泄漏到 infrastructure 外部。
- Persistence model 与 domain/application DTO 之间显式映射。
- 默认事务边界放在 persistence adapter 方法上。
- 不要让事务跨越外部 I/O。
- Cache/search/index 同步属于 infrastructure 行为，除非它具有业务语义。
- 当仓库区分 `repository/jpa` 与泛化 persistence 包时，JPA entity 与 Spring Data repository 放在 repository/JPA adapter 包内。

## Interfaces 规则

放在 Interfaces：

- REST/RPC controller。
- Message consumer。
- Scheduler/job/CLI adapter。
- Request 和 response DTO。
- 将同进程业务事件翻译为 command 的业务事件 listener。

规则：

- 不放业务规则。
- 不直接调用 repository 或 infrastructure adapter。
- 通过项目的 application entrypoint 分发 command/query，例如 `CommandBus`、`QueryBus` 或本地等价物。
- 显式映射 request DTO -> command/query，以及 result -> response。
- Validation 限于语法/协议层输入校验。
- 当仓库存在 request/response 子包约定时，HTTP DTO 放入对应子包。

## 事件与跨边界契约

使用以下顺序：

1. 定义本地 domain event。
2. 在 aggregate 行为中注册或返回该事件。
3. 定义或演进跨边界 contract/schema。
4. 在 infrastructure 中把本地 domain event 映射为 integration message。
5. 通过项目选择的可靠机制发布。
6. Consumer 把 message 翻译为 command。
7. Handler/domain model 执行业务响应。

规则：

- Domain event 是本地事件；integration message 是外部契约。
- Outbox mapper 属于 infrastructure，也是发布侧唯一应该导入生成 integration-event 类的地方。
- Consumer 必须幂等。
- 破坏性 message/API 变更需要版本化或并行变更。
- 跨服务原子性不存在；使用最终一致、重试、补偿和对账。

## 计划清单

每个功能计划都必须覆盖：

- Domain model 变更。
- Command path 变更。
- Query path 变更。
- Ports 与 adapters。
- Interface adapters。
- 跨边界时的 event/contracts。
- 存储形状变化时的数据迁移。
- 按层划分的测试。

如果某项不适用，写明“不需要”以及原因。

## 评审清单

发现以下情况时，拒绝或要求修改生成代码：

- Domain 导入 application/infrastructure/framework 行为。
- Handler 包含应属于 domain 的业务 guard。
- Controller/consumer 直接调用 repository 或 adapter。
- Persistence entity 泄漏到 application/interfaces。
- Command handler 返回 query result 或 response DTO。
- Query handler 不必要地重新水合 domain aggregate。
- 事务包住外部 I/O。
- 跨边界事件消费缺少幂等。
- Domain event 在 infrastructure 外直接映射到生成的 integration schema。
- 测试只有 component/e2e，缺少 domain/application unit test。
