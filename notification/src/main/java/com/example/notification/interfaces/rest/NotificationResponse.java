package com.example.notification.interfaces.rest;

import com.example.notification.application.query.notification.NotificationView;

import java.util.UUID;

record NotificationResponse(UUID id, UUID customerId, String recipientEmail,
                             String channel, String subject, String deliveryStatus) {

    static NotificationResponse from(NotificationView view) {
        return new NotificationResponse(view.id(), view.customerId(), view.recipientEmail(),
                view.channel(), view.subject(), view.deliveryStatus());
    }
}
