package com.example.notification.domain.ports;

import com.example.notification.domain.model.Notification;

import java.util.List;
import java.util.UUID;

/** Write-side repository port — domain concept: "the collection of all Notifications". */
public interface NotificationRepository {
    Notification save(Notification notification);

    List<Notification> findByCustomerId(UUID customerId, int page, int size);
}
