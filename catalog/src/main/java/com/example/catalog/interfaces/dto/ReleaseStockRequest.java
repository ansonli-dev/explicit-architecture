package com.example.catalog.interfaces.dto;

import java.util.UUID;

public record ReleaseStockRequest(UUID orderId, int quantity) {}
