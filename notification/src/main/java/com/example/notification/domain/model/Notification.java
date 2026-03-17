package com.example.notification.domain.model;

import com.example.notification.domain.event.NotificationFailed;
import com.example.notification.domain.event.NotificationSent;
import com.example.seedwork.domain.AggregateRoot;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification — Aggregate Root of the notification bounded context.
 */
public class Notification extends AggregateRoot<NotificationId> {

    private final UUID customerId;
    private final String recipientEmail;
    private final Channel channel;
    private final Payload payload;
    private DeliveryStatus deliveryStatus;
    private String failureReason;

    private Notification(NotificationId id, UUID customerId, String recipientEmail,
            Channel channel, Payload payload, DeliveryStatus status) {
        super(id);
        this.customerId = customerId;
        this.recipientEmail = recipientEmail;
        this.channel = channel;
        this.payload = payload;
        this.deliveryStatus = status;
    }

    public static Notification create(UUID customerId, String recipientEmail,
            Channel channel, Payload payload) {
        return new Notification(NotificationId.generate(), customerId, recipientEmail,
                channel, payload, DeliveryStatus.PENDING);
    }

    public static Notification reconstitute(NotificationId id, UUID customerId, String recipientEmail,
            Channel channel, Payload payload,
            DeliveryStatus status, String failureReason) {
        Notification n = new Notification(id, customerId, recipientEmail, channel, payload, status);
        n.failureReason = failureReason;
        return n;
    }

    public void markSent() {
        this.deliveryStatus = DeliveryStatus.SENT;
        registerEvent(new NotificationSent(UUID.randomUUID(), this.getId(), this.customerId, Instant.now()));
    }

    public void markFailed(String reason) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.failureReason = reason;
        registerEvent(new NotificationFailed(UUID.randomUUID(), this.getId(), this.customerId, reason, Instant.now()));
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public Channel getChannel() {
        return channel;
    }

    public Payload getPayload() {
        return payload;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
