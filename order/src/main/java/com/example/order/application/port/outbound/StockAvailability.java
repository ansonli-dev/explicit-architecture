package com.example.order.application.port.outbound;

import java.util.UUID;

public record StockAvailability(UUID bookId, int availableStock) {}
