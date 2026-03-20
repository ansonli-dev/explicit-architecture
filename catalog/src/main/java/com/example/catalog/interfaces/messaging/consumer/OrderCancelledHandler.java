package com.example.catalog.interfaces.messaging.consumer;

import com.example.catalog.application.command.book.ReleaseStockCommand;
import com.example.events.v1.OrderCancelled;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Releases reserved stock for each item in a cancelled order.
 * Dispatches one {@link ReleaseStockCommand} per item — each is handled idempotently
 * by the catalog domain (release is a no-op if the reservation no longer exists).
 */
@Component
public class OrderCancelledHandler implements RetryableKafkaHandler<OrderCancelled> {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledHandler.class);

    private final CommandBus commandBus;

    public OrderCancelledHandler(CommandBus commandBus) {
        this.commandBus = commandBus;
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
        event.getItems().forEach(item ->
                commandBus.dispatch(new ReleaseStockCommand(
                        UUID.fromString(item.getBookId().toString()),
                        orderId,
                        item.getQuantity())));
    }
}
