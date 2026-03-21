# ADR-003: shared-events 作为 Avro Schema SDK

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## 背景

微服务通过 Kafka 异步通信，每条消息都有生产者和消费者。我们需要决定：

1. **Schema 的格式**：用什么描述事件结构？
2. **Schema 的归属**：谁拥有、谁维护？
3. **代码的生成**：消费者/生产者如何得到强类型的消息类？
4. **兼容性的保障**：如何防止 schema 破坏性变更导致运行时错误？

### 备选方案

| 方案 | 描述 | 问题 |
|---|---|---|
| A — 各服务各自定义 DTO | 生产者和消费者各写各的 class | schema 漂移，编译期无法发现不匹配 |
| B — 共享 Java Records | `shared-events` 放 Java records | 无序列化标准，无 schema 注册中心兼容性检查 |
| C — Avro + Schema Registry | `.avsc` 定义 schema，代码生成，Schema Registry 做兼容性守门 | 引入 Avro 和 Schema Registry 依赖，但这是行业标准做法 |
| D — Protobuf | 类似 C，不同序列化格式 | 与 Confluent 生态系统（Schema Registry、Debezium）集成不如 Avro 成熟 |

---

## 决策

采用 **方案 C**：以 `shared-events` 作为**集中化的 Avro Schema SDK**。

### shared-events 的职责

```
.avsc 文件（源）
    ↓ Avro Gradle Plugin（构建时）
Java SpecificRecord 类（产物）
    ↓ publishToMavenLocal
各微服务 dependency（消费）
```

`shared-events` 模块：
- 是 `.avsc` schema 文件的**唯一存放地**
- 通过 Avro Gradle Plugin（`com.github.davidmc24.gradle.plugin.avro`）在**构建时生成** Java 类
- 生成的类是 `org.apache.avro.specific.SpecificRecord` 的子类，携带完整类型信息
- 发布为 Gradle/Maven library（Demo 中发布到 `mavenLocal()`），各服务声明 `implementation` 依赖
- **不包含任何业务逻辑、Spring Bean、领域对象**

### 哪些事件属于 shared-events

仅放**跨服务消费**的事件。单个服务内部的事件（如 order 内的 read-model projection 触发器）不在此处。

| 事件 | 生产者 | 消费者 |
|---|---|---|
| `OrderPlaced` | order | notification |
| `OrderConfirmed` | order | notification |
| `OrderCancelled` | order | notification、catalog |
| `OrderShipped` | order | notification |
| `StockReserved` | catalog | order |
| `StockReleased` | catalog | — |

### Schema Registry 的角色

Schema Registry（Confluent）作为运行时的**兼容性守门员**：

- 生产者首次发送消息时，`KafkaAvroSerializer` 自动向 Schema Registry 注册 schema，获得 `schema_id`
- 消息格式：`[magic byte(1)] + [schema_id(4)] + [avro binary payload]`
- 消费者收到消息后，从 Schema Registry 按 `schema_id` 获取 schema（本地缓存），完成反序列化
- Schema Registry 在注册新版本时**强制检查兼容性**（默认 `BACKWARD` 模式），阻止破坏性变更

### Debezium 与 Avro 的集成

Debezium Connect 使用 `AvroConverter` 将 Outbox 消息以 Avro 格式发布到 Kafka，schema 自动注册到 Schema Registry：

```json
"key.converter": "io.confluent.connect.avro.AvroConverter",
"key.converter.schema.registry.url": "http://schema-registry:8081",
"value.converter": "io.confluent.connect.avro.AvroConverter",
"value.converter.schema.registry.url": "http://schema-registry:8081"
```

### 领域事件与 Avro 消息的映射

微服务的领域层仍然使用**纯 Java records** 作为领域事件（domain event），Avro 类只出现在 `infrastructure/messaging/` 适配器层：

```
domain event (pure Java record)         ← 领域层，无 Avro 依赖
    ↓ 在 infrastructure/messaging/ 适配器中映射
Avro SpecificRecord (shared-events)     ← 基础设施层，Kafka 序列化
```

这保持了领域层对 Avro 和 Kafka 的零依赖（ADR-001 架构规则）。

---

## 影响

### 积极影响

- **编译期契约**：生产者和消费者依赖同一个生成类，字段不匹配在编译时发现
- **Schema 进化有工具保障**：Schema Registry 在 CI 或部署时阻止不兼容变更
- **高效序列化**：Avro 二进制格式比 JSON 小 50–80%，对高吞吐场景有意义
- **自文档化**：`.avsc` 文件中的 `doc` 字段就是活文档，消费团队可直接查看
- **Debezium 原生支持**：Debezium Outbox Event Router 与 Avro + Schema Registry 集成成熟

### 消极影响

- **构建依赖**：各服务均为独立 Gradle 项目，必须先在 `shared-events/` 目录执行 `./gradlew publishToMavenLocal`，各服务才能使用最新 SDK
- **Avro 学习成本**：开发者需了解 Avro schema 语法和演进规则
- **生成代码风格**：Avro 生成的 Java 类（Builder 模式，非 record）与项目其余代码风格（records）不一致，但仅出现在 infrastructure 层

### 各服务引用方式（独立模块）

各服务为独立 Gradle 项目（非 multi-project），统一通过 `mavenLocal()` 依赖 shared-events SDK：

```bash
# 修改 schema 后，在 shared-events/ 目录执行
./gradlew publishToMavenLocal
```

```kotlin
// 各服务 build.gradle.kts
repositories {
    mavenLocal()   // 优先查找本地发布的 shared-events
    maven { url = uri("https://packages.confluent.io/maven/") }
    mavenCentral()
}

dependencies {
    implementation("com.example:shared-events:0.1.0")  // 跟随版本号更新
}
```

### 演进路径

生产阶段，`shared-events` 可接入私有 Maven 仓库（Nexus、GitHub Packages、Artifactory），各服务切换为坐标引用，其余发布和消费逻辑不变。Demo 阶段保持 `mavenLocal()`。

---

## SDK 正式化（Demo 阶段补充）

在基础 schema → 代码生成能力之上，`shared-events` 正式确立三层职责：

| 层 | 内容 |
|---|---|
| **Schema 层** | `.avsc` 源文件（唯一权威）+ Schema Registry 本地预注册脚本 |
| **SDK 层** | Avro SpecificRecord 生成类 + `mavenLocal()` 发布 |
| **文档层** | 事件目录、演进规则、`CHANGELOG.md` |

**Schema Registry 预注册**（`schema-registry/register-schemas.sh`）允许开发者在 Schema Registry 启动后立即验证 schema 兼容性并完成注册，无需等待服务运行时才发现序列化错误：

```bash
./schema-registry/register-schemas.sh              # 注册所有 schema
./schema-registry/register-schemas.sh --check-only # 仅检查兼容性
```

版本策略遵循 [ADR-008](ADR-008-shared-events-versioning.md) 中的约定。
