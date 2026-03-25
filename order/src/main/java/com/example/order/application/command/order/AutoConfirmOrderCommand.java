package com.example.order.application.command.order;

import com.example.seedwork.application.command.Command;
import java.util.UUID;

/**
 * Command to auto-confirm an order if its total is at or below the given threshold.
 * Dispatched by {@code OrderPlacedAutoConfirmListener} when an order is placed.
 */
public record AutoConfirmOrderCommand(UUID orderId, long thresholdCents, String thresholdCurrency)
        implements Command<Void> {
}
