package com.example.notification.interfaces.messaging.consumer;

import com.example.events.v1.OrderPlaced;
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
class OrderPlacedHandlerTest {

    @Mock SendNotificationCommandHandler commandHandler;
    @Mock OrderPlaced event;

    private OrderPlacedHandler handler;
    private final UUID customerId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new OrderPlacedHandler(commandHandler);
    }

    @Test
    void givenOrderPlacedEvent_whenHandle_thenSendNotificationCommandDispatchedWithCorrectFields() {
        // Arrange
        when(event.getCustomerId()).thenReturn(customerId.toString());
        when(event.getOrderId()).thenReturn(orderId.toString());
        when(event.getTotalCents()).thenReturn(20000L);
        when(event.getCurrency()).thenReturn("CNY");

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
    }

    @Test
    void givenOrderPlacedEvent_whenEventType_thenReturnsOrderPlacedClass() {
        assertThat(handler.eventType()).isEqualTo(OrderPlaced.class);
    }

    @Test
    void givenOrderPlacedEvent_whenEventId_thenReturnsUuidFromEvent() {
        // Arrange
        when(event.getEventId()).thenReturn(eventId.toString());

        // Act
        UUID result = handler.eventId(event);

        // Assert
        assertThat(result).isEqualTo(eventId);
    }
}
