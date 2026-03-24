package com.example.notification.application.query.notification;

import com.example.notification.domain.ports.NotificationRepository;
import com.example.notification.domain.model.Channel;
import com.example.notification.domain.model.DeliveryStatus;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationId;
import com.example.notification.domain.model.Payload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListNotificationsQueryHandlerTest {

    @Mock NotificationRepository notificationRepository;

    private ListNotificationsQueryHandler handler;
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new ListNotificationsQueryHandler(notificationRepository);
    }

    @Test
    void givenNotificationsExist_whenHandle_thenAllMappedAndReturned() {
        // Arrange
        Notification notification = Notification.reconstitute(
                NotificationId.generate(), customerId, "user@example.com",
                Channel.EMAIL, new Payload("Order Placed", "Your order has been placed."),
                DeliveryStatus.SENT, null);
        when(notificationRepository.findByCustomerId(customerId, 0, 20)).thenReturn(List.of(notification));

        // Act
        List<NotificationResult> responses = handler.handle(new ListNotificationsQuery(customerId, 0, 20));

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).customerId()).isEqualTo(customerId);
        assertThat(responses.get(0).deliveryStatus()).isEqualTo("SENT");
        assertThat(responses.get(0).subject()).isEqualTo("Order Placed");
    }

    @Test
    void givenNoNotificationsForCustomer_whenHandle_thenEmptyListReturned() {
        // Arrange
        when(notificationRepository.findByCustomerId(any(), anyInt(), anyInt())).thenReturn(List.of());

        // Act
        List<NotificationResult> responses = handler.handle(new ListNotificationsQuery(customerId, 0, 20));

        // Assert
        assertThat(responses).isEmpty();
    }
}
