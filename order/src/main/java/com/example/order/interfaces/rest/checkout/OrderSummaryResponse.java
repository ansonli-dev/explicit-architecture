package com.example.order.interfaces.rest.checkout;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record OrderSummaryResponse(UUID orderId, String status, long totalMinor, String currency, Instant createdAt,
                              @JsonInclude(JsonInclude.Include.NON_NULL) List<OrderLineResponse> lines) {}
