package com.example.order.interfaces.dto;

import com.example.order.application.command.order.CancelOrderCommand;

import java.util.UUID;

public record CancelRequest(String reason) {

    public CancelOrderCommand toCommand(UUID orderId) {
        return new CancelOrderCommand(orderId, reason());
    }
}