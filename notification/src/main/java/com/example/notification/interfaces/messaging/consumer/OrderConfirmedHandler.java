package com.example.notification.interfaces.messaging.consumer;

import com.example.events.v1.OrderConfirmed;
import com.example.notification.application.command.notification.SendNotificationCommand;
import com.example.notification.domain.model.Channel;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderConfirmedHandler implements RetryableKafkaHandler<OrderConfirmed> {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmedHandler.class);

    private final CommandBus commandBus;

    public OrderConfirmedHandler(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @Override
    public Class<OrderConfirmed> eventType() {
        return OrderConfirmed.class;
    }

    @Override
    public UUID eventId(OrderConfirmed event) {
        return UUID.fromString(event.getEventId().toString());
    }

    @Override
    public void handle(OrderConfirmed event) {
        log.info("Processing OrderConfirmed: orderId={}", event.getOrderId());
        commandBus.dispatch(new SendNotificationCommand(
                UUID.fromString(event.getCustomerId().toString()),
                Channel.EMAIL,
                "订单已支付确认",
                "您的订单 " + event.getOrderId() + " 已支付，正在处理中。"));
    }
}
