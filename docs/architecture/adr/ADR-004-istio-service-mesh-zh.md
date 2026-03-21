# ADR-004: 使用 Istio 服务网格替代应用层弹性库

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## 背景

微服务之间通过网络相互调用，需要弹性机制：重试、超时、熔断器和双向 TLS。主要有两种方案：

**方案 A — 应用层库**（如 Resilience4j、Spring Retry）
- 弹性逻辑存在于应用代码中
- 每个服务团队自行配置策略
- 无需额外基础设施

**方案 B — 服务网格**（如 Istio、Linkerd）
- Sidecar 代理（Envoy）拦截所有流量
- 弹性策略通过 Kubernetes CRD（`VirtualService`、`DestinationRule`）配置
- 开箱即用地提供 mTLS、分布式追踪和流量指标

### 本项目中的同步调用

`order` 在下单前会调用 `catalog` 验证书籍库存（同步 REST 调用）。这是主要的服务间 HTTP 依赖。

---

## 决策

我们使用 **Istio** 处理所有跨服务弹性、mTLS 和流量管理。应用代码**不**为服务间调用实现重试、熔断器或超时逻辑。

### Istio 提供的能力

| 关注点 | Istio 机制 |
|---|---|
| 重试 | `VirtualService.http.retries` |
| 超时 | `VirtualService.http.timeout` |
| 熔断 | `DestinationRule.trafficPolicy.outlierDetection` |
| mTLS | `PeerAuthentication`（STRICT 模式） |
| 负载均衡 | `DestinationRule.trafficPolicy.loadBalancer` |
| 金丝雀发布 / 流量分割 | `VirtualService.http.route[].weight` |
| 分布式追踪 | Envoy sidecar 自动埋点并传播 `traceparent` |
| 流量指标 | Prometheus 抓取 Envoy `/stats/prometheus` |

### 配置示例

**catalog 调用的重试策略：**
```yaml
# infrastructure/k8s/istio/catalog-virtual.yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: catalog
spec:
  hosts:
    - catalog
  http:
    - retries:
        attempts: 3
        perTryTimeout: 2s
        retryOn: gateway-error,connect-failure,retriable-4xx
      timeout: 10s
      route:
        - destination:
            host: catalog
            port:
              number: 8081
```

**catalog 的熔断器：**
```yaml
# infrastructure/k8s/istio/catalog-destination-rule.yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: catalog
spec:
  host: catalog
  trafficPolicy:
    outlierDetection:
      consecutive5xxErrors: 5
      interval: 30s
      baseEjectionTime: 30s
      maxEjectionPercent: 100
```

### 应用代码的职责

应用代码**仅**处理业务层面的降级逻辑——即在所有重试均失败后应该做什么：

```java
// application/command/order/PlaceOrderCommandHandler.java
public Order placeOrder(PlaceOrderCommand cmd) {
    try {
        StockStatus stock = catalogPort.checkStock(cmd.bookId(), cmd.quantity());
        if (!stock.isAvailable()) {
            throw new InsufficientStockException(cmd.bookId());
        }
    } catch (CatalogServiceUnavailableException ex) {
        // 业务决策：catalog 不可达时拒绝下单
        throw new OrderCannotBePlacedException("Catalog unavailable, please try again later");
    }
    // ... 继续创建订单
}
```

---

## 影响

### 积极影响
- **关注点分离**：弹性策略是运维关注点，而非业务关注点。运维团队可在不修改代码的情况下调整重试/超时策略。
- **统一策略执行**：所有服务自动获得 mTLS、重试和熔断保护，无需逐一实现。
- **免费的可观测性**：Istio 通过 Envoy 指标为每对服务提供黄金信号（延迟、流量、错误、饱和度）。
- **零信任网络**：STRICT mTLS 意味着 Pod 之间没有明文流量，即使在集群内部也是如此。

### 消极影响
- **Istio 复杂性**：Istio 带来额外的运维开销（CRD 管理、sidecar 注入、Envoy 配置调试）。
- **本地开发不一致**：未安装 Istio 的本地 Kubernetes（minikube/kind）没有 sidecar 注入。开发者不能依赖 Istio 提供本地环境中预期的行为。

### 缓解措施
- 明确记录差异：重试/熔断行为仅在安装了 Istio 的 Kubernetes 环境中生效。
- 在 CI 中使用 Istio 的 `istioctl analyze` 在部署前捕获配置错误。

### 仍使用 Resilience4j 的场景
- 单个服务**内部**的弹性（如 Redis 连接池管理、ElasticSearch 客户端超时）——这些不在服务网格的覆盖范围内。
