package com.example.notification.infrastructure.repository.jpa;

import com.example.notification.application.port.outbound.NotificationRepository;
import com.example.notification.domain.model.Channel;
import com.example.notification.domain.model.DeliveryStatus;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.Payload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(NotificationPersistenceAdapter.class)
class NotificationPersistenceAdapterTest {

    @Autowired NotificationRepository notificationRepository;

    private Notification buildNotification(UUID customerId) {
        return Notification.create(customerId, "user@example.com", Channel.EMAIL,
                new Payload("Order Placed", "Your order has been placed."));
    }

    @Test
    void givenNewNotification_whenSave_thenNotificationCanBeFoundByCustomerId() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Notification notification = buildNotification(customerId);

        // Act
        notificationRepository.save(notification);

        // Assert
        List<Notification> found = notificationRepository.findByCustomerId(customerId, 0, 20);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getCustomerId()).isEqualTo(customerId);
        assertThat(found.get(0).getRecipientEmail()).isEqualTo("user@example.com");
        assertThat(found.get(0).getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void givenUnknownCustomerId_whenFindByCustomerId_thenReturnsEmptyList() {
        // Act
        List<Notification> found = notificationRepository.findByCustomerId(UUID.randomUUID(), 0, 20);

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void givenSentNotification_whenSave_thenDeliveryStatusPersistedAsSent() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Notification notification = buildNotification(customerId);
        notification.markSent();

        // Act
        notificationRepository.save(notification);

        // Assert
        List<Notification> found = notificationRepository.findByCustomerId(customerId, 0, 20);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void givenFailedNotification_whenSave_thenDeliveryStatusAndReasonPersisted() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        Notification notification = buildNotification(customerId);
        notification.markFailed("SMTP timeout");

        // Act
        notificationRepository.save(notification);

        // Assert
        List<Notification> found = notificationRepository.findByCustomerId(customerId, 0, 20);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(found.get(0).getFailureReason()).isEqualTo("SMTP timeout");
    }

    @Test
    void givenMultipleNotifications_whenFindByCustomerId_thenPaginationApplied() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            notificationRepository.save(buildNotification(customerId));
        }

        // Act
        List<Notification> page0 = notificationRepository.findByCustomerId(customerId, 0, 3);
        List<Notification> page1 = notificationRepository.findByCustomerId(customerId, 1, 3);

        // Assert
        assertThat(page0).hasSize(3);
        assertThat(page1).hasSize(2);
    }
}
