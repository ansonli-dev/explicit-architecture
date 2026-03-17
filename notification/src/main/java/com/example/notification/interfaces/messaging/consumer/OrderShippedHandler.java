package com.example.notification.interfaces.messaging.consumer;

import com.example.events.v1.OrderShipped;
import com.example.notification.application.command.notification.SendNotificationCommand;
import com.example.notification.application.command.notification.SendNotificationCommandHandler;
import com.example.notification.domain.model.Channel;
import com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderShippedHandler implements RetryableKafkaHandler<OrderShipped> {

    private static final Logger log = LoggerFactory.getLogger(OrderShippedHandler.class);

    private final SendNotificationCommandHandler commandHandler;

    public OrderShippedHandler(SendNotificationCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @Override
    public Class<OrderShipped> eventType() {
        return OrderShipped.class;
    }

    @Override
    public UUID eventId(OrderShipped event) {
        return UUID.fromString(event.getEventId().toString());
    }

    @Override
    public void handle(OrderShipped event) {
        log.info("Processing OrderShipped: orderId={}", event.getOrderId());
        commandHandler.handle(new SendNotificationCommand(
                UUID.fromString(event.getCustomerId().toString()),
                Channel.EMAIL,
                "订单已发货",
                "您的订单 " + event.getOrderId() + " 已发货，快递单号：" + event.getTrackingNumber()));
    }
}
