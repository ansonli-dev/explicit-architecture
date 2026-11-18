package com.example.order.interfaces.rest.v2;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record OrderSummaryResponseV2(UUID orderId, String status, long totalMinor, String currency, Instant createdAt,
                              @JsonInclude(JsonInclude.Include.NON_NULL) List<OrderLineResponse> lines) {}
