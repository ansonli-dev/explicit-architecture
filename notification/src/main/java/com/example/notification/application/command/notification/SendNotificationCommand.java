package com.example.notification.application.command.notification;

import com.example.notification.domain.model.Channel;
import com.example.seedwork.application.command.Command;

import java.util.UUID;

public record SendNotificationCommand(
        UUID customerId,
        Channel channel,
        String subject,
        String body) implements Command<Void> {
}
