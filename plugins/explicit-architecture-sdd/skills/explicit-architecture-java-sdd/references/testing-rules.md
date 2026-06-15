# 测试规则

这些规则适用于 Java 17、Spring Boot 3、JUnit 5、AssertJ、Mockito 单元测试，以及使用一次性真实/近真实依赖的集成测试。本文件是决策指导；只有在生成对应测试时才加载 `assets/templates/tests/` 下的完整测试模板。

## Fowler 测试类型到显式架构的映射

| 测试类型 | 边界 | 验证内容 | 数量 |
|---|---|---|---|
| Unit | 单个类或内存内对象簇 | 领域规则、handler 编排、DTO/事件翻译 | 最多 |
| Integration | 一个 adapter 加外部协议/组件 | 技术边界假设 | 少量，高价值 |
| Component | 通过公开入口驱动一个服务/组件 | 服务行为，依赖被 stub 或包含在边界内 | 关键用例 |
| Contract | 跨边界 API/message/schema | 消费方期望与提供方兼容性 | 每个边界 |
| End-to-End | 完整部署后的业务旅程 | 系统级协作与部署接线 | 极少 |

## 测试模板路由

生成测试时，只读取需要的模板：

| 需求 | 模板 |
|---|---|
| Aggregate/value object/domain service unit test | `assets/templates/tests/domain-unit-test.java` |
| Command handler unit test | `assets/templates/tests/handler-unit-test.java` |
| Query handler unit test | `assets/templates/tests/query-handler-unit-test.java` |
| REST controller slice test | `assets/templates/tests/controller-slice-test.java` |
| 使用 Testcontainers 的 persistence adapter integration test | `assets/templates/tests/persistence-adapter-testcontainers-test.java` |
| 使用 Testcontainers 的 message broker integration test | `assets/templates/tests/message-broker-testcontainers-test.java` |
| Message broker embedded fallback test | `assets/templates/tests/message-broker-embedded-test.java` |
| Cache adapter integration test | `assets/templates/tests/cache-adapter-testcontainers-test.java` |
| Search/read-model adapter integration test | `assets/templates/tests/search-adapter-testcontainers-test.java` |
| External HTTP client integration test | `assets/templates/tests/http-client-wiremock-test.java` |
| File storage adapter integration test | `assets/templates/tests/file-storage-tempdir-test.java` |
| Email/SMS/payment/provider client test | `assets/templates/tests/provider-client-wiremock-test.java` |
| Raw WebSocket receive integration test | `assets/templates/tests/websocket-receive-integration-test.java` |
| STOMP-over-WebSocket receive integration test | `assets/templates/tests/stomp-receive-integration-test.java` |

模板使用 `{{PLACEHOLDER}}` 标记。替换每个标记，并删除对最终测试无帮助的注释。

## Unit Test 规则

Domain unit test：

- 不启动 Spring context。
- 不依赖数据库、网络、消息系统、缓存或文件系统。
- 除非无法避免，不对 domain collaborator 使用 Mockito。
- 测行为和不变量，不测试私有实现。

Handler unit test：

- 不启动 Spring context。
- 只 mock 或 fake port。
- 不重复测试 domain 内部细节。
- 验证 load -> domain call -> save -> result 行为。
- Query handler 验证 port 选择、fallback、空结果，以及存在时的授权/作用域。

Interface unit/slice test：

- Mock application entrypoint。
- 验证 route/method/status、DTO 映射、validation 和 error response。
- 不验证 persistence 或 domain 行为。

## Adapter Integration Test 规则

- 测 adapter 及真实或协议等价的外部边界。
- 优先使用一次性真实或近真实依赖，例如 Testcontainers 管理的服务。
- 测 persistence adapter 时，如果项目使用 migration，就运行真实 schema migration。
- 调用远端第三方 API 慢、贵、不稳定或不安全时，使用协议级 stub。
- 只有当 embedded component 对该边界足够接近真实依赖时，才作为 fallback。
- Adapter integration test 不以 Mockito mock 作为主要边界；mock 属于 handler/unit test。
- 场景保持少量：happy path、mapping、重要错误、transaction/consistency 行为。
- 不在这里跑完整业务旅程。

推荐环境：

| Adapter 边界 | 首选集成环境 | Fallback |
|---|---|---|
| Persistence adapter | Testcontainers 真实数据库 + 真实 migration | 只有方言/事务行为足够等价时才用 embedded database |
| Message producer/consumer | Testcontainers 真实 broker | 轻量 smoke test 可用 embedded broker |
| Cache adapter | Testcontainers 真实 cache 服务 | In-memory fake 只用于 component test，不用于 adapter integration test |
| Search/read-model adapter | Testcontainers 真实 search/read-model 服务 | Repository-level fake 只用于 handler test |
| External HTTP client | WireMock 或 MockWebServer 等协议级 stub；关键 contract 可用 sandbox API | Mockito 只用于 handler test |
| File/object storage adapter | 视协议复杂度使用 Testcontainers 服务、sandbox 服务或临时文件系统 | 本地 file adapter 可用临时文件系统 |
| Email/SMS/payment adapter | Sandbox API 或协议级 stub | Fake sender 用于 component test |
| WebSocket adapter | `@SpringBootTest(RANDOM_PORT)` + 真实 WebSocket/STOMP client | 纯 message-routing 逻辑只写 handler unit test |

## Component Test 规则

- 通过公开接口驱动。
- 组件边界外依赖要 stub 或包含起来。
- 覆盖关键写/读 happy path 和少量重要错误。
- 不覆盖每个业务分支；低层测试负责它们。
- 异步行为使用 bounded polling。不要使用任意 sleep。

## Contract Test 规则

适用于：

- HTTP/RPC API。
- Message schema。
- File/batch interface。
- Public SDK。

规则：

- 捕获消费方期望。
- 尽可能在 CI 自动验证提供方兼容性。
- 优先使用兼容的增量变更。
- 破坏性迁移使用 parallel change。

## End-to-End Test 规则

仅用于：

- 跨多个组件的核心用户旅程。
- 真实 deployment/routing/auth 集成。
- 最终一致链路。
- 服务拆分/合并或通信架构变化后的回归。

规则：

- 数量少。
- 使用独立测试数据。
- 对最终一致使用 bounded polling。
- 在每个主要边界断言诊断状态。

## 变更到测试矩阵

| 变更 | 必需测试 |
|---|---|
| 新领域规则 | Domain unit；相关 handler unit |
| 新 command use case | Domain unit、command handler unit、interface test、component happy path |
| 新 query use case | Query handler unit、interface test、read adapter integration |
| 新 persistence shape | Persistence adapter integration、migration verification、相关 component test |
| 新 external client | Client integration、handler unit、跨边界时 contract test |
| 新 message event | Event mapper unit、schema compatibility、consumer unit、component test |
| 新 cache/search/file 行为 | Adapter integration、handler/query unit、component test |
| 新 WebSocket receive path | 使用真实 client 的 WebSocket integration test；路由逻辑写 handler unit test |
| 新 multi-component journey | 低层测试 + 一个 e2e happy path |

## 完成门禁

声明完成前：

- Domain/application 测试通过。
- 相关 adapter 测试通过，或明确说明无法运行的原因。
- 只有边界需要时才添加 component/contract/e2e 测试。
- 没有测试依赖任意 sleep 处理异步行为。
- 每个失败都能指向明确架构边界。
