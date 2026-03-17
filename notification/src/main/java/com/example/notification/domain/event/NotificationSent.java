package com.example.notification.domain.event;

import com.example.notification.domain.model.NotificationId;

import com.example.seedwork.domain.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record NotificationSent(UUID eventId, NotificationId notificationId, UUID customerId, Instant occurredAt)
        implements DomainEvent {
}
