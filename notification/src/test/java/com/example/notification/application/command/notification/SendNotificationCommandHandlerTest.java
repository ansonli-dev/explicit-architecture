package com.example.notification.application.command.notification;

import com.example.notification.application.port.outbound.CustomerClient;
import com.example.notification.application.port.outbound.EmailSender;
import com.example.notification.domain.ports.NotificationRepository;
import com.example.notification.domain.model.Channel;
import com.example.notification.domain.model.DeliveryStatus;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.Payload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendNotificationCommandHandlerTest {

    @Mock NotificationRepository notificationRepository;
    @Mock EmailSender emailSender;
    @Mock CustomerClient customerClient;

    private SendNotificationCommandHandler handler;
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new SendNotificationCommandHandler(notificationRepository, emailSender, customerClient);
    }

    @Test
    void givenCustomerEmailFound_whenEmailSentSuccessfully_thenNotificationSavedAsSent() {
        // Arrange
        when(customerClient.findEmail(customerId)).thenReturn(Optional.of("user@example.com"));
        var command = new SendNotificationCommand(customerId, Channel.EMAIL,
                "Order Placed", "Your order has been placed.");

        // Act
        handler.handle(command);

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        verify(emailSender).send(any(), any(Payload.class));
    }

    @Test
    void givenCustomerEmailFound_whenEmailSenderThrows_thenNotificationSavedAsFailed() {
        // Arrange
        when(customerClient.findEmail(customerId)).thenReturn(Optional.of("user@example.com"));
        doThrow(new RuntimeException("SMTP timeout")).when(emailSender).send(any(), any());
        var command = new SendNotificationCommand(customerId, Channel.EMAIL,
                "Order Placed", "Your order has been placed.");

        // Act
        handler.handle(command);

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).contains("SMTP timeout");
    }

    @Test
    void givenNoEmailFoundForCustomer_whenHandle_thenNotificationSavedAsFailedAndEmailNotSent() {
        // Arrange
        when(customerClient.findEmail(customerId)).thenReturn(Optional.empty());
        var command = new SendNotificationCommand(customerId, Channel.EMAIL,
                "Order Placed", "Your order has been placed.");

        // Act
        handler.handle(command);

        // Assert
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        verify(emailSender, never()).send(any(), any());
    }
}
