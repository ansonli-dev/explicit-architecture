package com.example.catalog.interfaces.messaging.consumer;

import com.example.catalog.application.command.book.ReleaseCheckoutCommand;
import com.example.catalog.application.command.book.ReleaseLegacyStockCommand;
import com.example.events.v1.OrderCancelled;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledHandler implements RetryableKafkaHandler<OrderCancelled> {

    private final CommandBus commands;

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
        UUID orderId = UUID.fromString(event.getOrderId().toString());
        switch (event.getReservationProtocol()) {
            case 1 -> releaseLegacy(event, orderId);
            case 2 -> {
                if (commands.dispatch(new ReleaseCheckoutCommand(orderId)) == ReleaseCheckoutCommand.Result.MISSING) {
                    log.warn("No checkout reservation for cancelled order {}; recording a release tombstone", orderId);
                }
            }
            default -> throw new IllegalStateException(
                    "Unsupported catalog reservation protocol: " + event.getReservationProtocol());
        }
    }

    private void releaseLegacy(OrderCancelled event, UUID orderId) {
        log.info("Processing legacy OrderCancelled: orderId={}, items={}", orderId, event.getItems().size());
        var items = event.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getBookId().toString()))
                .toList();
        for (var item : items) {
            if (item.getQuantity() <= 0) {
                log.warn("Skipping item with invalid quantity: orderId={}, bookId={}, quantity={}",
                        orderId, item.getBookId(), item.getQuantity());
                continue;
            }
            try {
                commands.dispatch(new ReleaseLegacyStockCommand(
                        UUID.fromString(item.getBookId().toString()), orderId, item.getQuantity()));
            } catch (Exception e) {
                log.error("Failed to release legacy stock for orderId={}, bookId={}", orderId, item.getBookId(), e);
            }
        }
    }
}
