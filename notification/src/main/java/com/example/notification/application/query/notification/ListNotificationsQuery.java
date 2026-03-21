package com.example.notification.application.query.notification;

import com.example.seedwork.application.query.Query;

import java.util.List;
import java.util.UUID;

public record ListNotificationsQuery(UUID customerId, int page, int size) implements Query<List<NotificationView>> {
}
