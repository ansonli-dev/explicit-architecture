package com.example.notification.interfaces.rest.response;

import com.example.notification.application.query.notification.NotificationView;

import java.util.UUID;

public record NotificationResponse(UUID id, UUID customerId, String recipientEmail,
                                   String channel, String subject, String deliveryStatus) {

    public static NotificationResponse from(NotificationView view) {
        return new NotificationResponse(view.id(), view.customerId(), view.recipientEmail(),
                view.channel(), view.subject(), view.deliveryStatus());
    }
}
