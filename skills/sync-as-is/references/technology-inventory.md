# As-Is 技术能力盘点

本文件说明如何建立可供 Target 设计和审查消费的 Technology Capability Profile。它记录现实，不选择未来实现。

## 目录

- [盘点范围](#盘点范围)
- [有效版本](#有效版本)
- [能力证据](#能力证据)
- [输出](#输出)

## 盘点范围

从可验证证据读取：

### Language and Runtime

- build manifest、compiler/toolchain target 和 lockfile；
- runtime image、CI matrix 和实际运行配置。

### Framework and Libraries

- dependency manifest、resolved dependency report；
- framework plugin、data-access/unit-of-work、serializer/schema、client/driver；
- test 与 runtime profile。

### Data and Messaging

- database product、edition、engine、extension、major/minor version 和配置；
- migration history、isolation 和 online-operation 能力；
- broker/product version、delivery、ordering、transaction、retry 和 schema 配置；
- cache/search/object store 的版本与部署模式。

### Deployment Environments

- local、test、staging、production 的有效组合；
- managed service 限制、region/topology 和 compatibility mode；
- rolling deployment、feature flag 与版本共存。

## 有效版本

同一仓库声明多个版本时，确认真正参与当前环境的 effective version：

1. 解析锁定或 resolved dependency；
2. 对照 compiler/runtime image；
3. 对照 deployment configuration 和 managed service metadata；
4. 记录环境差异；
5. 不默认选择 manifest 中最高版本。

版本、edition、extension 或配置无法确认时标记 `UNKNOWN`。不要用“某产品通常支持”补全证据。

## 能力证据

只记录 Target 判断可能依赖的实际能力，例如：

- language construct 和 compiler target；
- serializer/proxy/reflection 兼容；
- data-access create/update/delete ordering；
- storage constraint、generated representation、locking 和 transaction；
- broker delivery、ordering、retry、replay 和 schema evolution；
- migration、rolling deployment 和 observability。

证据优先：

1. 仓库锁定的 dependency/runtime metadata；
2. 绑定版本的官方 specification；
3. 本地 dependency source、driver/server metadata；
4. 受控最小实验；
5. 二手资料只用于发现线索。

本流程只报告 capability、limitation 和 confidence；不在 Current 盘点中选择未来实现。

## 输出

| Concern | Product / language / framework | Effective version/config | Environment | Relevant capability | Limitation | Evidence | Confidence |
|---|---|---|---|---|---|---|---|
| {{CONCERN}} | {{TECH}} | {{VERSION}} | {{ENVIRONMENT}} | {{CAPABILITY}} | {{LIMITATION}} | {{EVIDENCE}} | `CONFIRMED / INFERRED / UNKNOWN` |

Profile 不要求罗列完整依赖树，但必须覆盖会改变机制、数据模型、兼容或交付的能力。
