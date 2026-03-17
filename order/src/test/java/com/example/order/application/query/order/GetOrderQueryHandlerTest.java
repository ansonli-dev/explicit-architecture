package com.example.order.application.query.order;

import com.example.order.application.port.outbound.OrderPersistence;
import com.example.order.application.port.outbound.OrderSearchRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrderQueryHandlerTest {

    @Mock OrderSearchRepository orderSearchRepository;
    @Mock OrderPersistence orderPersistence;

    private GetOrderQueryHandler handler;
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID bookId = UUID.randomUUID();
    private OrderDetailResponse esResponse;

    @BeforeEach
    void setUp() {
        handler = new GetOrderQueryHandler(orderSearchRepository, orderPersistence);
        esResponse = new OrderDetailResponse(orderId, customerId, "user@example.com",
                "PENDING", List.of(), 20_000, "CNY");
    }

    @Test
    void givenOrderInElasticSearch_whenHandle_thenReturnedFromEsWithoutFallingBack() {
        // Arrange
        when(orderSearchRepository.findById(orderId)).thenReturn(Optional.of(esResponse));

        // Act
        OrderDetailResponse response = handler.handle(new GetOrderQuery(orderId));

        // Assert
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(orderPersistence, never()).findById(any());
    }

    @Test
    void givenEsMissAndOrderInPostgres_whenHandle_thenFallenBackToPostgres() {
        // Arrange
        var item = OrderItem.create(bookId, "Clean Code", new Money(10_000, "CNY"), 2);
        Order dbOrder = Order.reconstitute(
                OrderId.of(orderId), CustomerId.of(customerId), "user@example.com",
                new OrderStatus.Pending(), List.of(item), new Money(20_000, "CNY"));

        when(orderSearchRepository.findById(orderId)).thenReturn(Optional.empty());
        when(orderPersistence.findById(OrderId.of(orderId))).thenReturn(Optional.of(dbOrder));

        // Act
        OrderDetailResponse response = handler.handle(new GetOrderQuery(orderId));

        // Assert
        assertThat(response.orderId()).isEqualTo(orderId);
        verify(orderPersistence).findById(OrderId.of(orderId));
    }

    @Test
    void givenOrderNotFoundInEitherStore_whenHandle_thenThrowsOrderNotFoundException() {
        // Arrange
        when(orderSearchRepository.findById(orderId)).thenReturn(Optional.empty());
        when(orderPersistence.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new GetOrderQuery(orderId)))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
