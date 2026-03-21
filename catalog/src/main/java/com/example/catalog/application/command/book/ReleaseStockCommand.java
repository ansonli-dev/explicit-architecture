package com.example.catalog.application.command.book;

import java.util.UUID;

public record ReleaseStockCommand(UUID bookId, UUID orderId, int quantity) {
}
