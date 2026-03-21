package com.example.notification.application.query.notification;

import java.util.UUID;

public record NotificationView(
        UUID id,
        UUID customerId,
        String recipientEmail,
        String channel,
        String subject,
        String deliveryStatus) {
}
