package com.example.order.application.command.order;

import com.example.order.application.port.outbound.CatalogClient;
import com.example.order.application.port.outbound.OrderPersistence;
import com.example.order.application.query.order.OrderNotFoundException;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelOrderCommandHandlerTest {

    @Mock OrderPersistence orderPersistence;
    @Mock CatalogClient catalogClient;

    private CancelOrderCommandHandler handler;
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID bookId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new CancelOrderCommandHandler(orderPersistence, catalogClient);
    }

    @Test
    void givenExistingPendingOrder_whenHandle_thenOrderSavedAndStockReleased() {
        // Arrange
        var item = OrderItem.create(bookId, "Clean Code", new Money(10_000, "CNY"), 2);
        Order pendingOrder = Order.reconstitute(
                OrderId.of(orderId), CustomerId.of(customerId), "user@example.com",
                new OrderStatus.Pending(), List.of(item), new Money(20_000, "CNY"));

        when(orderPersistence.findById(OrderId.of(orderId))).thenReturn(Optional.of(pendingOrder));
        when(orderPersistence.save(any())).thenReturn(pendingOrder);

        // Act
        handler.handle(new CancelOrderCommand(orderId, "customer request"));

        // Assert
        verify(orderPersistence).save(pendingOrder);
        verify(catalogClient).releaseStock(eq(bookId), eq(orderId), eq(2));
    }

    @Test
    void givenNonExistentOrder_whenHandle_thenThrowsOrderNotFoundException() {
        // Arrange
        when(orderPersistence.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new CancelOrderCommand(orderId, "reason")))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
