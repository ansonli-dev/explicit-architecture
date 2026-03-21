package com.example.catalog.application.command.book;

import java.util.UUID;

public record ReserveStockCommand(UUID bookId, UUID orderId, int quantity) {
}
