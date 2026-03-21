# ADR-011: 使用 SwaggerHub + PactFlow 双向契约测试进行 API 治理

- **Status**: Accepted
- **Date**: 2026-03-13
- **Deciders**: Architecture Team

---

## 背景

我们需要一个解决方案来应对两个相关但不同的关切：

1. **API 注册中心**：已发布 REST API 规范的唯一真实来源，使消费方在编写代码前能够发现并理解提供方契约。
2. **契约测试**：自动化验证提供方的 API 实现与消费方所依赖的内容相符，在变更进入生产环境前发现破坏性变更。

### 评估方案

| 方案 | API 注册中心 | 契约测试 |
|---|---|---|
| A——不使用工具 | 各团队阅读源代码 | 人工、临时 |
| B——Git 中的 OpenAPI | 规范文件提交至仓库 | 对运行中服务使用 Schemathesis |
| C——独立使用 SwaggerHub | SwaggerHub 托管规范 | 无契约级别验证 |
| D——传统 Pact CDC | 无 | 提供方针对消费方 pact 重放到实际代码 |
| **E——SwaggerHub + PactFlow BDCT** | **SwaggerHub 托管规范** | **PactFlow 交叉验证 OAS 与 Pact；无需提供方重放** |

### 为何选择 BDCT 而非传统 Pact CDC

传统 Pact CDC 要求**提供方团队针对其代码库运行消费方 pact**，造成组织层面的耦合：提供方 CI 依赖消费方 pact 状态。这在以下场景会失效：

- 提供方与消费方归属不同团队，发布节奏不同
- 提供方有复杂的环境依赖（数据库迁移、Kafka 等）
- 消费方添加了一个 pact 测试，提供方团队必须先响应，双方才能各自发布

**双向契约测试**将两端解耦：

- 消费方发布 pact（描述其调用内容及所需响应结构）
- 提供方发布 OAS 规范（描述其实际实现）
- PactFlow 负责两者的比对——提供方侧无需执行任何测试

---

## 决策

使用 **SwaggerHub** 作为 API 注册中心，使用 **PactFlow**（SmartBear）对所有 REST 服务边界进行双向契约测试。

### 范围

`order-service` → `catalog-service` 的 REST 边界是唯一的同步 HTTP 依赖：

| 消费方 | 提供方 | 交互 |
|---|---|---|
| order-service | catalog-service | `GET /api/v1/books/{id}/stock`, `POST /api/v1/books/{id}/stock/reserve`, `POST /api/v1/books/{id}/stock/release` |

notification 服务没有同步 HTTP 依赖。异步 Kafka 事件由 Avro Schema Registry（ADR-003）负责，不属于 Pact 的范畴。

---

## 实现

### 组件职责

```
order-service (Consumer)                  catalog-service (Provider)
─────────────────────────────────         ──────────────────────────────────
CatalogClientPactTest.java                springdoc-openapi starter
  └─ defines 3 interactions                 └─ exposes /v3/api-docs at runtime
  └─ runs against Pact mock server
  └─ generates build/pacts/*.json

CI: pact-broker publish                   CI: curl /v3/api-docs → catalog-oas.json
      └─► PactFlow                              └─► PactFlow (provider contract)
                                                └─► SwaggerHub (API registry)
                        ▼
              PactFlow cross-validates
              consumer pact vs provider OAS
                        ▼
                 can-i-deploy gate
```

### springdoc-openapi（catalog 服务）

已在 catalog 中添加 `springdoc-openapi-starter-webmvc-ui`，暴露以下端点：
- `GET /v3/api-docs`——机器可读的 OpenAPI JSON（供 CI 和 PactFlow 消费）
- `GET /swagger-ui.html`——人类可读的交互式 UI

无需额外配置——springdoc 自动发现 `@RestController` 注解。

### 消费方 Pact 测试（`order/src/test/java/.../contract/CatalogClientPactTest.java`）

- 使用 `@Tag("contract")` 注解，支持 CI 定向执行（无需 Testcontainers）
- 直接以 Pact mock server URL 实例化 `CatalogRestClient`（无 Spring 上下文）
- 三个交互：`checkStock`、`reserveStock`、`releaseStock`
- 输出：`order/build/pacts/order-service-catalog-service.json`

消费方 pact 只描述 order-service **实际使用**的响应字段，而非完整的提供方响应结构。这是 BDCT 的核心原则：最小化耦合。

### 提供方契约（CI 步骤，非测试类）

不存在 `CatalogProviderPactTest.java`——这在 BDCT 中是有意为之的。CI 直接获取运行中的规范：

```bash
curl "${CATALOG_BASE_URL}/v3/api-docs" -o catalog-oas.json
pactflow publish-provider-contract catalog-oas.json \
  --provider catalog-service --provider-app-version $VERSION --verification-success
```

### CI 工作流（`.github/workflows/contract-test.yml`）

共三个 Job：

| Job | 步骤 |
|---|---|
| `consumer` | 运行 `@Tag("contract")` 测试 → 发布 pact 到 PactFlow |
| `provider` | 从测试环境获取 `/v3/api-docs` → 发布到 PactFlow + SwaggerHub |
| `can-i-deploy` | 对两个服务执行 `pact-broker can-i-deploy`；不兼容时阻断 |

所有 Pact CLI 操作使用 `pactfoundation/pact-cli` Docker 镜像，无需安装 gem。

### 所需 GitHub Secrets

| Secret | 描述 |
|---|---|
| `PACTFLOW_BASE_URL` | PactFlow 实例 URL（如 `https://yourorg.pactflow.io`） |
| `PACTFLOW_API_TOKEN` | PactFlow 读写 API Token |
| `SWAGGERHUB_API_KEY` | 用于上传规范的 SwaggerHub API Key |
| `SWAGGERHUB_OWNER` | SwaggerHub 组织/所有者名称 |
| `E2E_CATALOG_BASE_URL` | 测试环境中 catalog 服务的 Base URL（同样被 e2e.yml 使用） |

---

## 影响

### 积极影响

- **无提供方测试耦合**：catalog 团队永远不会被 order 的 pact 状态阻塞，每个服务可独立发布
- **OAS 单一真实来源**：springdoc 从实际控制器代码生成规范，不存在手工维护的 YAML 文件与实现偏离的问题
- **SwaggerHub 作为 API 门户**：消费方在编写集成代码前可浏览 `/swagger-ui.html`；规范按 Git SHA 版本化
- **破坏性变更检测**：若 catalog 变更破坏了任何消费方交互，PactFlow 的 `can-i-deploy` 门控会在 CI 中使 PR 失败，而不是在生产环境中才暴露问题
- **无需额外测试基础设施**：消费方测试无需 Testcontainers 即可运行，在 CI 中快速且低成本

### 消极影响

- **依赖运行中的测试环境**：提供方 Job 需从运行中的测试环境获取规范，若测试环境宕机，提供方 CI 步骤将失败
- **两个 SmartBear 账号**：SwaggerHub 和 PactFlow 是独立账号（尽管同属一个厂商）。PactFlow 免费套餐对团队数量有限制，扩容前需确认配额
- **BDCT 局限性**：PactFlow 验证 OAS *允许*消费方交互，但不执行实际的 catalog 代码。与 OAS schema 匹配但实现存在 Bug 的情况无法被 BDCT 发现——集成测试（ADR-001 测试金字塔）负责覆盖此类场景
- **CI 中需要 Docker**：`pactfoundation/pact-cli` 镜像需要 runner 上可用的 Docker（`ubuntu-latest` 默认提供）

### 本方案不替代的内容

| 关切点 | 处理方式 |
|---|---|
| Kafka 事件 schema 兼容性 | Avro Schema Registry（ADR-003） |
| catalog 服务内部正确性 | 单元测试 + 集成测试（Testcontainers） |
| 全栈验证 | E2E 测试（`.github/workflows/e2e.yml`） |
| 服务间 mTLS 与流量策略 | Istio（ADR-004） |
