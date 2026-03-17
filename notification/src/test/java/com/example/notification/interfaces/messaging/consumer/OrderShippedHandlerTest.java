package com.example.notification.interfaces.messaging.consumer;

import com.example.events.v1.OrderShipped;
import com.example.notification.application.command.notification.SendNotificationCommand;
import com.example.notification.application.command.notification.SendNotificationCommandHandler;
import com.example.notification.domain.model.Channel;
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
class OrderShippedHandlerTest {

    @Mock SendNotificationCommandHandler commandHandler;
    @Mock OrderShipped event;

    private OrderShippedHandler handler;
    private final UUID customerId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new OrderShippedHandler(commandHandler);
    }

    @Test
    void givenOrderShippedEvent_whenHandle_thenSendNotificationCommandDispatchedWithTrackingNumber() {
        // Arrange
        when(event.getCustomerId()).thenReturn(customerId.toString());
        when(event.getOrderId()).thenReturn(orderId.toString());
        when(event.getTrackingNumber()).thenReturn("SF1234567890");

        // Act
        handler.handle(event);

        // Assert
        ArgumentCaptor<SendNotificationCommand> captor = ArgumentCaptor.forClass(SendNotificationCommand.class);
        verify(commandHandler).handle(captor.capture());

        SendNotificationCommand command = captor.getValue();
        assertThat(command.customerId()).isEqualTo(customerId);
        assertThat(command.channel()).isEqualTo(Channel.EMAIL);
        assertThat(command.subject()).isNotBlank();
        assertThat(command.body()).contains(orderId.toString());
        assertThat(command.body()).contains("SF1234567890");
    }

    @Test
    void givenOrderShippedEvent_whenEventType_thenReturnsOrderShippedClass() {
        assertThat(handler.eventType()).isEqualTo(OrderShipped.class);
    }

    @Test
    void givenOrderShippedEvent_whenEventId_thenReturnsUuidFromEvent() {
        // Arrange
        when(event.getEventId()).thenReturn(eventId.toString());

        // Act
        UUID result = handler.eventId(event);

        // Assert
        assertThat(result).isEqualTo(eventId);
    }
}
