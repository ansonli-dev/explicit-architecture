package com.example.notification.domain.model;

import com.example.notification.domain.event.NotificationFailed;
import com.example.notification.domain.event.NotificationSent;
import com.example.seedwork.domain.DomainEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private final UUID customerId = UUID.randomUUID();
    private final Payload payload = new Payload("Order Placed", "Your order has been placed.");

    @Nested
    class Create {

        @Test
        void givenValidArgs_whenCreate_thenStatusIsPendingAndNoEvents() {
            // Act
            Notification notification = Notification.create(customerId, "user@example.com", Channel.EMAIL, payload);

            // Assert
            assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(notification.getCustomerId()).isEqualTo(customerId);
            assertThat(notification.getRecipientEmail()).isEqualTo("user@example.com");
            assertThat(notification.getChannel()).isEqualTo(Channel.EMAIL);
            assertThat(notification.getFailureReason()).isNull();
            assertThat(notification.pullDomainEvents()).isEmpty();
        }
    }

    @Nested
    class MarkSent {

        @Test
        void givenPendingNotification_whenMarkSent_thenStatusIsSentAndEventRegistered() {
            // Arrange
            Notification notification = Notification.create(customerId, "user@example.com", Channel.EMAIL, payload);

            // Act
            notification.markSent();

            // Assert
            assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
            assertThat(notification.getFailureReason()).isNull();

            List<DomainEvent> events = notification.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(NotificationSent.class);

            NotificationSent event = (NotificationSent) events.get(0);
            assertThat(event.customerId()).isEqualTo(customerId);
            assertThat(event.notificationId()).isEqualTo(notification.getId());
        }

        @Test
        void givenNotificationMarkedSent_whenPullDomainEventsTwice_thenSecondPullIsEmpty() {
            // Arrange
            Notification notification = Notification.create(customerId, "user@example.com", Channel.EMAIL, payload);
            notification.markSent();

            // Act
            notification.pullDomainEvents(); // first pull clears events

            // Assert
            assertThat(notification.pullDomainEvents()).isEmpty();
        }
    }

    @Nested
    class MarkFailed {

        @Test
        void givenPendingNotification_whenMarkFailed_thenStatusIsFailedAndReasonSetAndEventRegistered() {
            // Arrange
            Notification notification = Notification.create(customerId, "user@example.com", Channel.EMAIL, payload);

            // Act
            notification.markFailed("SMTP timeout");

            // Assert
            assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(notification.getFailureReason()).isEqualTo("SMTP timeout");

            List<DomainEvent> events = notification.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(NotificationFailed.class);

            NotificationFailed event = (NotificationFailed) events.get(0);
            assertThat(event.customerId()).isEqualTo(customerId);
            assertThat(event.reason()).isEqualTo("SMTP timeout");
            assertThat(event.notificationId()).isEqualTo(notification.getId());
        }
    }

    @Nested
    class Reconstitute {

        @Test
        void givenExistingData_whenReconstitute_thenAllFieldsRestoredAndNoDomainEvents() {
            // Arrange
            NotificationId id = NotificationId.generate();

            // Act
            Notification notification = Notification.reconstitute(
                    id, customerId, "user@example.com", Channel.EMAIL, payload, DeliveryStatus.SENT, null);

            // Assert
            assertThat(notification.getId()).isEqualTo(id);
            assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
            assertThat(notification.pullDomainEvents()).isEmpty();
        }

        @Test
        void givenFailedNotificationData_whenReconstitute_thenFailureReasonRestored() {
            // Arrange
            NotificationId id = NotificationId.generate();

            // Act
            Notification notification = Notification.reconstitute(
                    id, customerId, "user@example.com", Channel.EMAIL, payload, DeliveryStatus.FAILED, "Connection refused");

            // Assert
            assertThat(notification.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(notification.getFailureReason()).isEqualTo("Connection refused");
        }
    }
}
