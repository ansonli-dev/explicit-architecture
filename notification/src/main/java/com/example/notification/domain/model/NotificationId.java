package com.example.notification.domain.model;

import com.example.seedwork.domain.DomainId;

import java.util.UUID;

public record NotificationId(UUID value) implements DomainId<UUID> {

    public NotificationId {
        if (value == null)
            throw new IllegalArgumentException("NotificationId must not be null");
    }

    public static NotificationId generate() {
        return new NotificationId(UUID.randomUUID());
    }

    public static NotificationId of(UUID v) {
        return new NotificationId(v);
    }
}
