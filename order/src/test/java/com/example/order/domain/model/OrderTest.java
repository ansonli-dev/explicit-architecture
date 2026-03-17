package com.example.order.domain.model;

import com.example.order.domain.event.OrderCancelled;
import com.example.order.domain.event.OrderConfirmed;
import com.example.order.domain.event.OrderPlaced;
import com.example.order.domain.event.OrderShipped;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Order aggregate state machine.
 * No Spring context, no mocks — pure domain logic tests (Sociable style).
 */
class OrderTest {

    private static Order createTestOrder() {
        var customerId = CustomerId.of(UUID.randomUUID());
        var item = new OrderItem(UUID.randomUUID(), UUID.randomUUID(), "Clean Code",
                new Money(4500, "CNY"), 2);
        return Order.create(customerId, "user@example.com", List.of(item), new Money(9000, "CNY"));
    }

    // ─── Creation ─────────────────────────────────────────────────────────────

    @Nested
    class Create {

        @Test
        void givenValidItems_whenCreate_thenStatusIsPending() {
            // Act
            Order order = createTestOrder();

            // Assert
            assertThat(order.getStatus()).isInstanceOf(OrderStatus.Pending.class);
        }

        @Test
        void givenEmptyItemList_whenCreate_thenThrowsIllegalArgument() {
            // Arrange
            var customerId = CustomerId.of(UUID.randomUUID());

            // Act & Assert
            assertThatThrownBy(() -> Order.create(customerId, "user@example.com", List.of(), new Money(0, "CNY")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── Place ────────────────────────────────────────────────────────────────

    @Nested
    class Place {

        @Test
        void givenPendingOrder_whenPlace_thenOrderPlacedEventReturned() {
            // Arrange
            Order order = createTestOrder();

            // Act
            OrderPlaced event = order.place();

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.orderId()).isEqualTo(order.getId());
            assertThat(event.customerId()).isEqualTo(order.getCustomerId());
        }

        @Test
        void givenPendingOrder_whenPlace_thenEventRegisteredOnAggregate() {
            // Arrange
            Order order = createTestOrder();

            // Act
            order.place();

            // Assert
            assertThat(order.pullDomainEvents())
                    .hasSize(1)
                    .first().isInstanceOf(OrderPlaced.class);
        }
    }

    // ─── Confirm ──────────────────────────────────────────────────────────────

    @Nested
    class Confirm {

        @Test
        void givenPendingOrder_whenConfirm_thenStatusIsConfirmed() {
            // Arrange
            Order order = createTestOrder();

            // Act
            order.confirm();

            // Assert
            assertThat(order.getStatus()).isInstanceOf(OrderStatus.Confirmed.class);
        }

        @Test
        void givenPendingOrder_whenConfirm_thenOrderConfirmedEventReturned() {
            // Arrange
            Order order = createTestOrder();

            // Act
            OrderConfirmed event = order.confirm();

            // Assert
            assertThat(event.orderId()).isEqualTo(order.getId());
        }

        @Test
        void givenConfirmedOrder_whenConfirmAgain_thenThrowsOrderStateException() {
            // Arrange
            Order order = createTestOrder();
            order.confirm();

            // Act & Assert
            assertThatThrownBy(order::confirm)
                    .isInstanceOf(OrderStateException.class);
        }
    }

    // ─── Ship ─────────────────────────────────────────────────────────────────

    @Nested
    class Ship {

        @Test
        void givenConfirmedOrder_whenShip_thenStatusIsShipped() {
            // Arrange
            Order order = createTestOrder();
            order.confirm();

            // Act
            order.ship("TRACK-001");

            // Assert
            assertThat(order.getStatus()).isInstanceOf(OrderStatus.Shipped.class);
            assertThat(((OrderStatus.Shipped) order.getStatus()).trackingNumber()).isEqualTo("TRACK-001");
        }

        @Test
        void givenConfirmedOrder_whenShip_thenOrderShippedEventReturned() {
            // Arrange
            Order order = createTestOrder();
            order.confirm();

            // Act
            OrderShipped event = order.ship("TRACK-001");

            // Assert
            assertThat(event.trackingNumber()).isEqualTo("TRACK-001");
            assertThat(event.orderId()).isEqualTo(order.getId());
        }

        @Test
        void givenPendingOrder_whenShip_thenThrowsOrderStateException() {
            // Arrange
            Order order = createTestOrder();

            // Act & Assert
            assertThatThrownBy(() -> order.ship("TRACK-001"))
                    .isInstanceOf(OrderStateException.class);
        }
    }

    // ─── Cancel ───────────────────────────────────────────────────────────────

    @Nested
    class Cancel {

        @Test
        void givenPendingOrder_whenCancel_thenStatusIsCancelled() {
            // Arrange
            Order order = createTestOrder();

            // Act
            order.cancel("customer request");

            // Assert
            assertThat(order.getStatus()).isInstanceOf(OrderStatus.Cancelled.class);
            assertThat(((OrderStatus.Cancelled) order.getStatus()).reason()).isEqualTo("customer request");
        }

        @Test
        void givenConfirmedOrder_whenCancel_thenStatusIsCancelled() {
            // Arrange
            Order order = createTestOrder();
            order.confirm();

            // Act
            order.cancel("out of stock");

            // Assert
            assertThat(order.getStatus()).isInstanceOf(OrderStatus.Cancelled.class);
        }

        @Test
        void givenPendingOrder_whenCancel_thenOrderCancelledEventReturned() {
            // Arrange
            Order order = createTestOrder();

            // Act
            OrderCancelled event = order.cancel("customer request");

            // Assert
            assertThat(event.orderId()).isEqualTo(order.getId());
            assertThat(event.reason()).isEqualTo("customer request");
        }

        @Test
        void givenShippedOrder_whenCancel_thenThrowsOrderStateException() {
            // Arrange
            Order order = createTestOrder();
            order.confirm();
            order.ship("TRACK-001");

            // Act & Assert
            assertThatThrownBy(() -> order.cancel("too late"))
                    .isInstanceOf(OrderStateException.class)
                    .hasMessageContaining("shipped");
        }
    }
}
