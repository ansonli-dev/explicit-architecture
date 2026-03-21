package com.example.catalog.interfaces.rest.request;

import java.util.UUID;

public record ReleaseStockRequest(UUID orderId, int quantity) {}
