package com.example.order.application.command.order;

import com.example.order.application.port.outbound.CatalogClient;
import com.example.order.application.port.outbound.OrderPersistence;
import com.example.order.application.query.order.StockCheckResponse;
import com.example.order.domain.model.InsufficientStockException;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.service.OrderPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOrderCommandHandlerTest {

    @Mock OrderPersistence orderPersistence;
    @Mock CatalogClient catalogClient;

    // OrderPricingService is a pure domain service — use real instance
    private final OrderPricingService pricingService = new OrderPricingService();

    private PlaceOrderCommandHandler handler;
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID bookId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new PlaceOrderCommandHandler(orderPersistence, catalogClient, pricingService);
    }

    @Test
    void givenValidCommandAndSufficientStock_whenHandle_thenOrderSavedAndResultReturned() {
        // Arrange
        var command = new PlaceOrderCommand(customerId, "user@example.com",
                List.of(new PlaceOrderCommand.OrderItem(bookId, "Clean Code", 10_000, "CNY", 2)));

        when(catalogClient.checkStock(bookId)).thenReturn(new StockCheckResponse(bookId, 10));
        when(orderPersistence.save(any())).thenReturn(buildSavedOrder());

        // Act
        PlaceOrderResult result = handler.handle(command);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isNotNull();
        assertThat(result.status()).isEqualTo("PLACED");
        verify(catalogClient).reserveStock(eq(bookId), any(), eq(2));
        verify(orderPersistence).save(any(Order.class));
    }

    @Test
    void givenInsufficientStock_whenHandle_thenThrowsInsufficientStockException() {
        // Arrange
        var command = new PlaceOrderCommand(customerId, "user@example.com",
                List.of(new PlaceOrderCommand.OrderItem(bookId, "Clean Code", 10_000, "CNY", 5)));

        when(catalogClient.checkStock(bookId)).thenReturn(new StockCheckResponse(bookId, 2));

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOf(InsufficientStockException.class);
    }

    private Order buildSavedOrder() {
        var item = com.example.order.domain.model.OrderItem.create(bookId, "Clean Code", new Money(10_000, "CNY"), 2);
        return Order.reconstitute(
                OrderId.of(orderId), CustomerId.of(customerId), "user@example.com",
                new OrderStatus.Placed(), List.of(item), new Money(20_000, "CNY"));
    }
}
