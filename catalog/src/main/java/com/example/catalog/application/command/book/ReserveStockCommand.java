package com.example.catalog.application.command.book;

import com.example.seedwork.application.command.Command;

import java.util.UUID;

public record ReserveStockCommand(UUID bookId, UUID orderId, int quantity) implements Command<ReserveStockResult> {
}
