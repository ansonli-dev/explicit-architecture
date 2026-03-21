package com.example.catalog.interfaces.messaging.consumer;

import com.example.catalog.application.command.book.ReleaseStockCommand;
import com.example.catalog.application.port.inbound.ReleaseStockUseCase;
import com.example.events.v1.OrderCancelled;
import com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Releases reserved stock for each item in a cancelled order.
 * Invokes {@link ReleaseStockUseCase} per item — each is handled idempotently
 * by the catalog domain (release is a no-op if the reservation no longer exists).
 */
@Component
public class OrderCancelledHandler implements RetryableKafkaHandler<OrderCancelled> {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledHandler.class);

    private final ReleaseStockUseCase releaseStock;

    public OrderCancelledHandler(ReleaseStockUseCase releaseStock) {
        this.releaseStock = releaseStock;
    }

    @Override
    public Class<OrderCancelled> eventType() {
        return OrderCancelled.class;
    }

    @Override
    public UUID eventId(OrderCancelled event) {
        return UUID.fromString(event.getEventId().toString());
    }

    @Override
    public void handle(OrderCancelled event) {
        log.info("Processing OrderCancelled: orderId={}, items={}", event.getOrderId(), event.getItems().size());
        UUID orderId = UUID.fromString(event.getOrderId().toString());
        for (var item : event.getItems()) {
            if (item.getQuantity() <= 0) {
                log.warn("Skipping item with invalid quantity: orderId={}, bookId={}, quantity={}",
                        orderId, item.getBookId(), item.getQuantity());
                continue;
            }
            try {
                releaseStock.handle(new ReleaseStockCommand(
                        UUID.fromString(item.getBookId().toString()),
                        orderId,
                        item.getQuantity()));
            } catch (Exception e) {
                // Log and continue — partial failure must not block remaining items.
                // Idempotent release (no-op if reservation no longer exists) makes retry safe.
                log.error("Failed to release stock for orderId={}, bookId={} — will not retry this item",
                        orderId, item.getBookId(), e);
            }
        }
    }
}
