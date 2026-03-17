package com.example.order.application.command.order;

import com.example.seedwork.application.command.Command;

import java.util.UUID;

public record CancelOrderCommand(UUID orderId, String reason) implements Command<Void> {
}
