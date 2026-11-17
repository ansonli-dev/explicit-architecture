package com.example.order.interfaces.rest.v2;

import java.util.UUID;

record OrderSummaryResponseV2(UUID orderId, String status, long totalMinor, String currency) {}
