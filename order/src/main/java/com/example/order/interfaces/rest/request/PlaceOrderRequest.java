package com.example.order.interfaces.rest.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotNull UUID customerId,
        @NotBlank String customerEmail,
        @NotNull @NotEmpty List<@NotNull Item> items) {

    public record Item(
            @NotNull UUID bookId,
            @NotBlank String bookTitle,
            @Positive long unitPriceCents,
            @NotBlank String currency,
            @Min(1) int quantity) {}
}
