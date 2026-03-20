package com.example.order.interfaces.dto;

public record CancelOrderRequest(String reason) {

    public CancelOrderRequest {
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Cancel reason must not be blank");
    }
}