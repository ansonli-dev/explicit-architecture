# Changelog

所有值得记录的变更都在此文件中跟踪。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，版本遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

> **规则**：每次修改 `.avsc` 文件、`build.gradle.kts` 版本号，或新增/删除事件时，**必须**在此文件记录。

---

## [0.1.0] - 2026-03-04

### Added

- **OrderPlaced**（`com.example.events.v1`）：客户成功下单且库存预留完成时发布
  - 生产者：`order`
  - 消费者：`notification`
  - 字段：`eventId`, `orderId`, `customerId`, `customerEmail`, `items[]`, `totalCents`, `currency`, `occurredAt`

- **OrderConfirmed**（`com.example.events.v1`）：订单确认时发布
  - 生产者：`order`
  - 消费者：`notification`
  - 字段：`eventId`, `orderId`, `customerId`, `occurredAt`

- **OrderCancelled**（`com.example.events.v1`）：订单取消时发布
  - 生产者：`order`
  - 消费者：`notification`, `catalog`
  - 字段：`eventId`, `orderId`, `customerId`, `reason`, `occurredAt`

- **OrderShipped**（`com.example.events.v1`）：订单发货时发布
  - 生产者：`order`
  - 消费者：`notification`
  - 字段：`eventId`, `orderId`, `customerId`, `trackingNumber`, `occurredAt`

- **StockReserved**（`com.example.events.v1`）：库存预留成功时发布
  - 生产者：`catalog`
  - 消费者：`order`
  - 字段：`eventId`, `bookId`, `orderId`, `quantity`, `occurredAt`

- **StockReleased**（`com.example.events.v1`）：库存释放时发布（订单取消）
  - 生产者：`catalog`
  - 消费者：—（暂无下游消费者，预留扩展）
  - 字段：`eventId`, `bookId`, `orderId`, `quantity`, `occurredAt`

### Notes

- 初始版本，建立 `com.example.events.v1` 命名空间
- 所有字段均包含 `doc` 文档说明
- 金额字段统一使用 `long`（分）+ `string`（货币码）避免浮点精度问题
