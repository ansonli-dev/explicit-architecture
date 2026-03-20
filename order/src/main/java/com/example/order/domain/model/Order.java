package com.example.order.domain.model;

import com.example.order.domain.event.OrderCancelled;
import com.example.order.domain.event.OrderConfirmed;
import com.example.order.domain.event.OrderPlaced;
import com.example.order.domain.event.OrderShipped;
import com.example.seedwork.domain.AggregateRoot;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Order — Aggregate Root of the order bounded context.
 * All state transitions emit domain events; never transitions silently.
 */
public class Order extends AggregateRoot<OrderId> {

    private final CustomerId customerId;
    private final String customerEmail; // snapshot at place-time
    private OrderStatus status;
    private final List<OrderItem> items;
    private final Money totalAmount;

    private Order(OrderId id, CustomerId customerId, String customerEmail,
            OrderStatus status, List<OrderItem> items, Money totalAmount) {
        super(id);
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.status = status;
        this.items = Collections.unmodifiableList(items);
        this.totalAmount = totalAmount;
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a new Order with a pre-calculated final price.
     * <p>
     * The caller (application service) is responsible for deriving
     * {@code finalTotal} via {@link com.example.order.domain.service.OrderPricingService}
     * before invoking this factory. Keeping price calculation outside the
     * aggregate preserves Single Responsibility: Order manages its state
     * machine; OrderPricingService owns discount policy.
     */
    /** Creates a new Order with a pre-generated ID (use when the ID must be known before persistence). */
    public static Order create(OrderId id, CustomerId customerId, String customerEmail,
            List<OrderItem> items, Money finalTotal) {
        if (customerEmail == null || customerEmail.isBlank())
            throw new IllegalArgumentException("Customer email must not be blank");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("Order must have at least one item");
        validateCurrencyConsistency(items);
        return new Order(id, customerId, customerEmail,
                new OrderStatus.Pending(), List.copyOf(items), finalTotal);
    }

    /** Creates a new Order with an auto-generated ID. */
    public static Order create(CustomerId customerId, String customerEmail,
            List<OrderItem> items, Money finalTotal) {
        return create(OrderId.generate(), customerId, customerEmail, items, finalTotal);
    }

    /**
     * Creates an Order, validating that stock is sufficient for each item.
     * Stock data must be pre-fetched by the caller (handler).
     */
    public static Order create(OrderId id, CustomerId customerId, String customerEmail,
            List<OrderItem> items, Money finalTotal, Map<UUID, Integer> availableStock) {
        if (customerEmail == null || customerEmail.isBlank())
            throw new IllegalArgumentException("Customer email must not be blank");
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("Order must have at least one item");
        validateCurrencyConsistency(items);
        for (OrderItem item : items) {
            int available = availableStock.getOrDefault(item.bookId(), 0);
            if (available < item.quantity()) {
                throw new InsufficientStockException(item.bookId(), item.quantity(), available);
            }
        }
        return new Order(id, customerId, customerEmail,
                new OrderStatus.Pending(), List.copyOf(items), finalTotal);
    }

    /** Creates a new Order with an auto-generated ID and stock validation. */
    public static Order create(CustomerId customerId, String customerEmail,
            List<OrderItem> items, Money finalTotal, Map<UUID, Integer> availableStock) {
        return create(OrderId.generate(), customerId, customerEmail, items, finalTotal, availableStock);
    }

    public static Order reconstitute(OrderId id, CustomerId customerId, String customerEmail,
            OrderStatus status, List<OrderItem> items, Money totalAmount) {
        return new Order(id, customerId, customerEmail, status, items, totalAmount);
    }

    // ─── Domain Behaviour ─────────────────────────────────────────────────────

    public OrderPlaced place() {
        assertStatus(OrderStatus.Pending.class, "place");
        this.status = new OrderStatus.Placed();
        OrderPlaced event = new OrderPlaced(UUID.randomUUID(), this.getId(), this.customerId,
                this.customerEmail, this.items, this.totalAmount, Instant.now());
        registerEvent(event);
        return event;
    }

    public OrderConfirmed confirm() {
        assertStatus(OrderStatus.Placed.class, "confirm");
        this.status = new OrderStatus.Confirmed();
        OrderConfirmed event = new OrderConfirmed(UUID.randomUUID(), this.getId(), this.customerId, Instant.now());
        registerEvent(event);
        return event;
    }

    public OrderShipped ship(String trackingNumber) {
        assertStatus(OrderStatus.Confirmed.class, "ship");
        this.status = new OrderStatus.Shipped(trackingNumber);
        OrderShipped event = new OrderShipped(UUID.randomUUID(), this.getId(), this.customerId, trackingNumber, Instant.now());
        registerEvent(event);
        return event;
    }

    public OrderCancelled cancel(String reason) {
        if (this.status instanceof OrderStatus.Shipped) {
            throw new OrderStateException("Cannot cancel a shipped order");
        }
        if (this.status instanceof OrderStatus.Cancelled) {
            throw new OrderStateException("Order is already cancelled");
        }
        this.status = new OrderStatus.Cancelled(reason);
        OrderCancelled event = new OrderCancelled(UUID.randomUUID(), this.getId(), this.customerId,
                reason, List.copyOf(this.items), Instant.now());
        registerEvent(event);
        return event;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private static void validateCurrencyConsistency(List<OrderItem> items) {
        String currency = items.get(0).unitPrice().currency();
        for (OrderItem item : items) {
            if (!item.unitPrice().currency().equals(currency)) {
                throw new IllegalArgumentException(
                        "All order items must share the same currency; mixed currencies are not supported");
            }
        }
    }

    private void assertStatus(Class<? extends OrderStatus> expected, String operation) {
        if (!expected.isInstance(this.status)) {
            throw new OrderStateException(
                    "Cannot " + operation + " an order in status: " + this.status.name());
        }
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public CustomerId getCustomerId() {
        return customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Money getTotalAmount() {
        return totalAmount;
    }
}
