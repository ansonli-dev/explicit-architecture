# ADR-007: Use Java 21 with Virtual Threads and Modern Language Features

- **Status**: Accepted
- **Date**: 2026-03-04
- **Deciders**: Architecture Team

---

## Context

Java 21 is the current LTS release and introduces several features relevant to this project:

- **Virtual Threads** (JEP 444): Lightweight threads managed by the JVM, enabling high-throughput I/O without reactive programming complexity.
- **Records** (stable since Java 16): Immutable data carriers with compact syntax.
- **Sealed Classes** (stable since Java 17): Restricted class hierarchies for exhaustive pattern matching.
- **Pattern Matching for switch** (stable since Java 21): Exhaustive type-safe switching on sealed hierarchies.
- **Sequenced Collections** (Java 21): Predictable ordering on collections.

We must decide how aggressively to use these features and what guidelines to establish.

---

## Decision

We adopt Java 21 as the minimum JVM version and actively use its modern features. The following guidelines apply:

### Virtual Threads

Enable virtual threads for all services via Spring Boot configuration:

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

**Effect**: Spring Boot's embedded Tomcat will use a virtual thread per request instead of a platform thread pool. JDBC connections block the virtual thread (not a platform thread), making I/O-bound services highly concurrent without reactive code.

**Consequence**: Do NOT use `parallelStream()` or `ForkJoinPool` for I/O tasks — these pin platform threads. Virtual threads are pinned inside `synchronized` blocks with native calls; prefer `ReentrantLock` over `synchronized` when lock contention is expected.

### Records for Data Carriers

Use records for all classes that are pure data containers with no mutable state:

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

**Prohibited**: Do not use records for JPA entities (JPA requires mutable state and a no-arg constructor).

### Sealed Classes for Domain States

Use sealed classes/interfaces with pattern matching for discriminated domain states:

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

### Text Blocks for SQL in Tests

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

### What We Do NOT Use

- **Reactive programming (Project Reactor / WebFlux)**: Virtual threads make blocking I/O efficient enough for our throughput requirements. Reactive code adds complexity without proportional benefit at this scale.
- **Kotlin**: Team standardizes on Java 21. Kotlin interop is not required.

---

## Consequences

### Positive
- **High throughput without reactive complexity**: Virtual threads allow thousands of concurrent I/O-bound requests with plain blocking JDBC/HTTP code.
- **Safer domain modeling**: Sealed classes + pattern matching make illegal states unrepresentable and enforce exhaustive handling at compile time.
- **Less boilerplate**: Records replace POJOs for DTOs, commands, queries, and value objects.
- **LTS stability**: Java 21 LTS is supported until 2031.

### Negative
- **Virtual thread pinning risk**: Synchronized native libraries (some JDBC drivers, some Caffeine cache internals) can pin virtual threads. Must audit dependencies.
- **Tooling maturity**: Some profilers and APM tools have incomplete virtual thread support. Verify OpenTelemetry agent compatibility.

### Dependency Requirements
- PostgreSQL JDBC driver 42.7+: fully virtual-thread safe
- Spring Boot 3.2+: full virtual thread support
- HikariCP 5.1+: virtual thread compatible connection pool
