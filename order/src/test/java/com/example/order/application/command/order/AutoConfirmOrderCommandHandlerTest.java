package com.example.order.application.command.order;

import com.example.order.domain.model.*;
import com.example.order.domain.ports.OrderPersistence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoConfirmOrderCommandHandlerTest {

    @Mock OrderPersistence orderPersistence;

    private AutoConfirmOrderCommandHandler handler;
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new AutoConfirmOrderCommandHandler(orderPersistence);
    }

    @Test
    void givenPlacedOrderBelowThreshold_whenHandle_thenConfirmsAndSaves() {
        Order order = placedOrderWithTotal(3000, "CNY");
        when(orderPersistence.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

        handler.handle(new AutoConfirmOrderCommand(orderId, 5000, "CNY"));

        verify(orderPersistence).save(order);
    }

    @Test
    void givenPlacedOrderAboveThreshold_whenHandle_thenDoesNotSave() {
        Order order = placedOrderWithTotal(6000, "CNY");
        when(orderPersistence.findById(OrderId.of(orderId))).thenReturn(Optional.of(order));

        handler.handle(new AutoConfirmOrderCommand(orderId, 5000, "CNY"));

        verify(orderPersistence, never()).save(any());
    }

    @Test
    void givenOrderNotFound_whenHandle_thenDoesNotThrow() {
        when(orderPersistence.findById(OrderId.of(orderId))).thenReturn(Optional.empty());

        handler.handle(new AutoConfirmOrderCommand(orderId, 5000, "CNY"));

        verify(orderPersistence, never()).save(any());
    }

    private Order placedOrderWithTotal(long cents, String currency) {
        var item = OrderItem.create(UUID.randomUUID(), "Book", new Money(cents, currency), 1);
        Order order = Order.create(OrderId.of(orderId), CustomerId.of(UUID.randomUUID()), "a@b.com",
                List.of(item), new Money(cents, currency));
        order.place();
        order.clearDomainEvents();
        return order;
    }
}
