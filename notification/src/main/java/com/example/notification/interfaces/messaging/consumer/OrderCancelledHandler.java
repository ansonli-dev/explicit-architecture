package com.example.notification.interfaces.messaging.consumer;

import com.example.events.v1.OrderCancelled;
import com.example.notification.application.command.notification.SendNotificationCommand;
import com.example.notification.domain.model.Channel;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
        log.info("Processing OrderCancelled: orderId={}", event.getOrderId());
        String reason = sanitizeReason(event.getReason());
        commandBus.dispatch(new SendNotificationCommand(
                UUID.fromString(event.getCustomerId().toString()),
                Channel.EMAIL,
                "订单取消通知",
                "您的订单 " + event.getOrderId() + " 已取消。原因：" + reason));
    }

    private static String sanitizeReason(String reason) {
        if (reason == null) return "";
        // Truncate by Unicode code points to avoid splitting surrogate pairs (e.g. emoji).
        // 500 code points ≈ 500 characters for BMP, fewer for supplementary-plane chars.
        String truncated = reason.codePointCount(0, reason.length()) > 500
                ? reason.substring(0, reason.offsetByCodePoints(0, 500))
                : reason;
        return truncated
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
