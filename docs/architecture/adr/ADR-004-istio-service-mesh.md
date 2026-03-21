# ADR-004: Use Istio Service Mesh Instead of Application-Level Resilience Libraries

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## Context

Microservices calling each other over the network need resilience mechanisms: retries, timeouts, circuit breakers, and mutual TLS. There are two primary approaches:

**Option A — Application-level libraries** (e.g., Resilience4j, Spring Retry)
- Resilience logic lives in the application code
- Each service team configures its own policies
- No additional infrastructure required

**Option B — Service mesh** (e.g., Istio, Linkerd)
- A sidecar proxy (Envoy) intercepts all traffic
- Resilience policies configured via Kubernetes CRDs (`VirtualService`, `DestinationRule`)
- mTLS, distributed tracing, and traffic metrics provided out of the box

### Synchronous Calls in This Project

`order` calls `catalog` to verify book availability before placing an order (synchronous REST call). This is the primary inter HTTP dependency.

---

## Decision

We use **Istio** for all cross resilience, mTLS, and traffic management. Application code does **not** implement retries, circuit breakers, or timeouts for inter calls.

### What Istio Provides

| Concern | Istio Mechanism |
|---|---|
| Retries | `VirtualService.http.retries` |
| Timeouts | `VirtualService.http.timeout` |
| Circuit breaking | `DestinationRule.trafficPolicy.outlierDetection` |
| mTLS | `PeerAuthentication` (STRICT mode) |
| Load balancing | `DestinationRule.trafficPolicy.loadBalancer` |
| Canary / traffic split | `VirtualService.http.route[].weight` |
| Distributed tracing | Envoy sidecar auto-instruments + propagates `traceparent` |
| Traffic metrics | Prometheus scraping Envoy `/stats/prometheus` |

### Configuration Examples

**Retry policy for catalog calls:**
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

**Circuit breaker for catalog:**
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

### Application Code Responsibility

Application code **only** handles business-level fallbacks — what to do when the call ultimately fails after all retries:

```java
// application/command/order/PlaceOrderCommandHandler.java
public Order placeOrder(PlaceOrderCommand cmd) {
    try {
        StockStatus stock = catalogPort.checkStock(cmd.bookId(), cmd.quantity());
        if (!stock.isAvailable()) {
            throw new InsufficientStockException(cmd.bookId());
        }
    } catch (CatalogServiceUnavailableException ex) {
        // Business decision: reject order when catalog is unreachable
        throw new OrderCannotBePlacedException("Catalog unavailable, please try again later");
    }
    // ... continue with order creation
}
```

---

## Consequences

### Positive
- **Separation of concerns**: Resilience policy is an operational concern, not a business concern. Ops teams can tune retry/timeout policies without code changes.
- **Uniform policy enforcement**: All services automatically get mTLS, retries, and circuit breaking without per implementation.
- **Observability for free**: Istio provides golden signals (latency, traffic, errors, saturation) for every service pair via Envoy metrics.
- **Zero trust networking**: STRICT mTLS means no plaintext traffic between pods, even within the cluster.

### Negative
- **Istio complexity**: Istio adds operational overhead (CRD management, sidecar injection, debugging Envoy config).
- **Local development mismatch**: Local Kubernetes (minikube/kind) without Istio installed has no sidecar injection. Developers must not rely on Istio for behavior they expect locally.

### Mitigations
- Document the gap explicitly: retry/circuit-breaker behavior only active in Kubernetes environments with Istio installed.
- Use Istio's `istioctl analyze` in CI to catch config errors before deployment.

### What We Still Use Resilience4j For
- **Internal** resilience within a single service (e.g., Redis connection pool management, ElasticSearch client timeouts) — these are not covered by the service mesh.
