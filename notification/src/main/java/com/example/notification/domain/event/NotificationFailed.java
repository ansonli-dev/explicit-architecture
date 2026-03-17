package com.example.notification.domain.event;

import com.example.notification.domain.model.NotificationId;

import com.example.seedwork.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record NotificationFailed(UUID eventId, NotificationId notificationId,
        UUID customerId, String reason, Instant occurredAt) implements DomainEvent {
}
