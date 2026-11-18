# Explicit Architecture · 在线书店

[English](README.md)

这是一个用在线书店展示领域边界、端口与适配器及 CQRS 的 Java 参考项目。[React 前端](https://github.com/ansonli-dev/react-reference-architecture) 通过 HTTP v1 读取目录并完成购书。读者用 Keycloak 登录，异步结算协调库存预留与订单创建。

- [Catalog](catalog/README.md) 管理目录、价格和库存。
- [Order](order/README.md) 管理报价、提交、订单和取消，并维护搜索读模型。
- [Notification](notification/README-zh.md) 消费订单事件。[Shared Events](shared-events/README-zh.md) 定义 Avro 事件契约。

## 快速开始

使用 JDK 21 和 Docker Compose。从 [书店运行手册](demo/README.md) 查看构建 Java 服务、启动前端、初始化库存和运行浏览器测试的命令。

Java HTTP 契约见 [Catalog v1](catalog/contracts/catalog-v1.yaml) 和 [Order v1](order/contracts/order-v1.yaml)。

## 项目文档

[架构规范](docs/architecture/architecture-spec-zh.md) · [Clarified Architecture](docs/architecture/clarified-architecture/clarified-architecture-zh.md) · [架构决策](docs/architecture/adr/README-zh.md) · [测试策略](docs/testing-strategy/README-zh.md)
