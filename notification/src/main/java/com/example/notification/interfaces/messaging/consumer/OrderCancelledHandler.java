package com.example.notification.interfaces.messaging.consumer;

import com.example.events.v1.OrderCancelled;
import com.example.notification.application.command.notification.SendNotificationCommand;
import com.example.notification.application.command.notification.SendNotificationCommandHandler;
import com.example.notification.domain.model.Channel;
import com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderCancelledHandler implements RetryableKafkaHandler<OrderCancelled> {

    private static final Logger log = LoggerFactory.getLogger(OrderCancelledHandler.class);

    private final SendNotificationCommandHandler commandHandler;

    public OrderCancelledHandler(SendNotificationCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
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
        commandHandler.handle(new SendNotificationCommand(
                UUID.fromString(event.getCustomerId().toString()),
                Channel.EMAIL,
                "订单取消通知",
                "您的订单 " + event.getOrderId() + " 已取消。原因：" + event.getReason()));
    }
}
