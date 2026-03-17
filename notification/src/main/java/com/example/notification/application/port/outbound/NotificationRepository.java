package com.example.notification.application.port.outbound;

import com.example.notification.domain.model.Notification;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository {
    Notification save(Notification notification);

    List<Notification> findByCustomerId(UUID customerId, int page, int size);
}
