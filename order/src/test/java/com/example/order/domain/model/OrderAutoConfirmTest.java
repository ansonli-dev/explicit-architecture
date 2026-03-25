package com.example.order.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAutoConfirmTest {

    private final Money threshold = Money.of(5000, "CNY");

    @Test
    void placedOrder_belowThreshold_autoConfirms() {
        Order order = placedOrderWithTotal(4999, "CNY");

        boolean confirmed = order.confirmIfEligible(threshold);

        assertThat(confirmed).isTrue();
        assertThat(order.getStatus()).isInstanceOf(OrderStatus.Confirmed.class);
        assertThat(order.peekDomainEvents()).hasSize(1)
                .first().isInstanceOf(com.example.order.domain.event.OrderConfirmed.class);
    }

    @Test
    void placedOrder_atThreshold_autoConfirms() {
        Order order = placedOrderWithTotal(5000, "CNY");

        boolean confirmed = order.confirmIfEligible(threshold);

        assertThat(confirmed).isTrue();
        assertThat(order.getStatus()).isInstanceOf(OrderStatus.Confirmed.class);
    }

    @Test
    void placedOrder_aboveThreshold_doesNotConfirm() {
        Order order = placedOrderWithTotal(5001, "CNY");

        boolean confirmed = order.confirmIfEligible(threshold);

        assertThat(confirmed).isFalse();
        assertThat(order.getStatus()).isInstanceOf(OrderStatus.Placed.class);
        assertThat(order.peekDomainEvents()).isEmpty();
    }

    @Test
    void pendingOrder_belowThreshold_doesNotConfirm() {
        Order order = pendingOrderWithTotal(1000, "CNY");

        boolean confirmed = order.confirmIfEligible(threshold);

        assertThat(confirmed).isFalse();
        assertThat(order.getStatus()).isInstanceOf(OrderStatus.Pending.class);
    }

    private Order placedOrderWithTotal(long cents, String currency) {
        var item = OrderItem.create(UUID.randomUUID(), "Book", new Money(cents, currency), 1);
        Order order = Order.create(CustomerId.of(UUID.randomUUID()), "a@b.com",
                List.of(item), new Money(cents, currency));
        order.place();
        order.clearDomainEvents();
        return order;
    }

    private Order pendingOrderWithTotal(long cents, String currency) {
        var item = OrderItem.create(UUID.randomUUID(), "Book", new Money(cents, currency), 1);
        return Order.create(CustomerId.of(UUID.randomUUID()), "a@b.com",
                List.of(item), new Money(cents, currency));
    }
}
