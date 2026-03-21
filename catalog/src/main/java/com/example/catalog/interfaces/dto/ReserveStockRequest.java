package com.example.catalog.interfaces.dto;

import java.util.UUID;

public record ReserveStockRequest(UUID orderId, int quantity) {}
