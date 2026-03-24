package com.example.notification.interfaces.rest.response;

import com.example.notification.application.query.notification.NotificationResult;

import java.util.UUID;

public record NotificationResponse(UUID id, UUID customerId, String recipientEmail,
                                   String channel, String subject, String deliveryStatus) {

    public static NotificationResponse from(NotificationResult result) {
        return new NotificationResponse(result.id(), result.customerId(), result.recipientEmail(),
                result.channel(), result.subject(), result.deliveryStatus());
    }
}
