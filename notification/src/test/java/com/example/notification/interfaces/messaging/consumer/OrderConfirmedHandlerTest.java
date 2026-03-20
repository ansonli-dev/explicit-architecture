package com.example.notification.interfaces.messaging.consumer;

import com.example.events.v1.OrderConfirmed;
import com.example.notification.application.command.notification.SendNotificationCommand;
import com.example.notification.domain.model.Channel;
import com.example.seedwork.application.bus.CommandBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderConfirmedHandlerTest {

    @Mock CommandBus commandBus;
    @Mock OrderConfirmed event;

    private OrderConfirmedHandler handler;
    private final UUID customerId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new OrderConfirmedHandler(commandBus);
    }

    @Test
    void givenOrderConfirmedEvent_whenHandle_thenSendNotificationCommandDispatchedWithCorrectFields() {
        // Arrange
        when(event.getCustomerId()).thenReturn(customerId.toString());
        when(event.getOrderId()).thenReturn(orderId.toString());

        // Act
        handler.handle(event);

        // Assert
        ArgumentCaptor<SendNotificationCommand> captor = ArgumentCaptor.forClass(SendNotificationCommand.class);
        verify(commandBus).dispatch(captor.capture());

        SendNotificationCommand command = captor.getValue();
        assertThat(command.customerId()).isEqualTo(customerId);
        assertThat(command.channel()).isEqualTo(Channel.EMAIL);
        assertThat(command.subject()).isNotBlank();
        assertThat(command.body()).contains(orderId.toString());
    }

    @Test
    void givenOrderConfirmedEvent_whenEventType_thenReturnsOrderConfirmedClass() {
        assertThat(handler.eventType()).isEqualTo(OrderConfirmed.class);
    }

    @Test
    void givenOrderConfirmedEvent_whenEventId_thenReturnsUuidFromEvent() {
        // Arrange
        when(event.getEventId()).thenReturn(eventId.toString());

        // Act
        UUID result = handler.eventId(event);

        // Assert
        assertThat(result).isEqualTo(eventId);
    }
}
