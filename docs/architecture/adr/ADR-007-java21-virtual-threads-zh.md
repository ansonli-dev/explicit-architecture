# ADR-007: 使用 Java 21 虚拟线程及现代语言特性

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## 背景

Java 21 是当前的 LTS 版本，引入了若干与本项目密切相关的特性：

- **虚拟线程**（JEP 444）：由 JVM 管理的轻量级线程，无需响应式编程即可实现高吞吐量 I/O。
- **Records**（自 Java 16 起稳定）：语法简洁的不可变数据载体。
- **密封类**（自 Java 17 起稳定）：受限的类层次结构，支持穷举模式匹配。
- **switch 的模式匹配**（自 Java 21 起稳定）：对密封层次结构进行穷举、类型安全的切换。
- **有序集合**（Java 21）：集合具有可预测的顺序。

我们需要决定对这些特性的使用力度，并建立相应的使用规范。

---

## 决策

我们采用 Java 21 作为最低 JVM 版本，并积极使用其现代特性。以下规范适用：

### 虚拟线程

通过 Spring Boot 配置为所有服务启用虚拟线程：

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

**效果**：Spring Boot 内嵌的 Tomcat 将为每个请求使用一个虚拟线程，而非平台线程池。JDBC 连接阻塞的是虚拟线程（而非平台线程），使 I/O 密集型服务无需响应式代码即可实现高并发。

**注意事项**：不要将 `parallelStream()` 或 `ForkJoinPool` 用于 I/O 任务——这些会钉住平台线程。虚拟线程在包含原生调用的 `synchronized` 块内会被钉住；当预期存在锁竞争时，优先使用 `ReentrantLock` 替代 `synchronized`。

### Records 用于数据载体

对所有无可变状态的纯数据容器类使用 records：

```java
// Commands
public record PlaceOrderCommand(UUID customerId, List<OrderItem> items) {
    public record OrderItem(UUID bookId, int quantity) {}
}

// Queries
public record FindOrdersByCustomerQuery(UUID customerId, int page, int size) {}

// Domain Events
public record OrderPlaced(UUID eventId, UUID orderId, UUID customerId, Instant occurredAt) {}

// Value Objects
public record Money(long cents, Currency currency) {
    public Money {
        if (cents < 0) throw new IllegalArgumentException("Money cannot be negative");
    }
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) throw new CurrencyMismatchException();
        return new Money(this.cents + other.cents, this.currency);
    }
}
```

**禁止**：不得将 records 用于 JPA 实体（JPA 需要可变状态和无参构造函数）。

### 密封类用于领域状态

对判别式领域状态使用密封类/接口与模式匹配：

```java
// domain/model/OrderStatus.java
public sealed interface OrderStatus permits
    OrderStatus.Pending,
    OrderStatus.Confirmed,
    OrderStatus.Shipped,
    OrderStatus.Cancelled {

    record Pending() implements OrderStatus {}
    record Confirmed(Instant confirmedAt) implements OrderStatus {}
    record Shipped(Instant shippedAt, String trackingNumber) implements OrderStatus {}
    record Cancelled(Instant cancelledAt, String reason) implements OrderStatus {}
}
```

```java
// Usage with exhaustive switch (compiler enforces all cases)
String statusLabel = switch (order.status()) {
    case OrderStatus.Pending p    -> "Awaiting confirmation";
    case OrderStatus.Confirmed c  -> "Confirmed at " + c.confirmedAt();
    case OrderStatus.Shipped s    -> "Shipped: " + s.trackingNumber();
    case OrderStatus.Cancelled c  -> "Cancelled: " + c.reason();
};
```

### 文本块用于测试中的 SQL

```java
@Test
void givenOrderExists_whenSearchByCustomer_thenFound() {
    jdbcTemplate.update("""
        INSERT INTO orders (id, customer_id, status, created_at)
        VALUES (?::uuid, ?::uuid, 'CONFIRMED', now())
        """, orderId, customerId);
    // ...
}
```

### 不使用的特性

- **响应式编程（Project Reactor / WebFlux）**：虚拟线程已能让阻塞式 I/O 满足我们的吞吐量需求，响应式代码在当前规模下带来的收益与其复杂性不成正比。
- **Kotlin**：团队统一使用 Java 21，无需 Kotlin 互操作。

---

## 影响

### 积极影响
- **无需响应式复杂度即可实现高吞吐量**：虚拟线程允许数千个并发 I/O 密集型请求使用普通阻塞式 JDBC/HTTP 代码。
- **更安全的领域建模**：密封类与模式匹配使非法状态无法表示，并在编译期强制穷举处理。
- **减少样板代码**：Records 替代 POJO 用于 DTO、命令、查询和值对象。
- **LTS 稳定性**：Java 21 LTS 支持至 2031 年。

### 消极影响
- **虚拟线程钉住风险**：某些使用同步原生库的 JDBC 驱动、Caffeine 缓存内部实现可能钉住虚拟线程，需要审计依赖项。
- **工具成熟度**：部分性能分析工具和 APM 工具对虚拟线程的支持尚不完整，需验证 OpenTelemetry agent 的兼容性。

### 依赖版本要求
- PostgreSQL JDBC driver 42.7+：完全兼容虚拟线程
- Spring Boot 3.2+：完整的虚拟线程支持
- HikariCP 5.1+：兼容虚拟线程的连接池
