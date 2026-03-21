package com.example.catalog.interfaces.rest.request;

import java.util.UUID;

public record ReserveStockRequest(UUID orderId, int quantity) {}
