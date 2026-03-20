package com.example.notification.interfaces.messaging.consumer;

import com.example.events.v1.OrderPlaced;
import com.example.notification.application.command.notification.SendNotificationCommand;
import com.example.notification.domain.model.Channel;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OrderPlacedHandler implements RetryableKafkaHandler<OrderPlaced> {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedHandler.class);

    private final CommandBus commandBus;

    public OrderPlacedHandler(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @Override
    public Class<OrderPlaced> eventType() {
        return OrderPlaced.class;
    }

    @Override
    public UUID eventId(OrderPlaced event) {
        return UUID.fromString(event.getEventId().toString());
    }

    @Override
    public void handle(OrderPlaced event) {
        log.info("Processing OrderPlaced: orderId={}", event.getOrderId());
        commandBus.dispatch(new SendNotificationCommand(
                UUID.fromString(event.getCustomerId().toString()),
                Channel.EMAIL,
                "订单确认 - 您的订单已下单",
                "您好，您的订单 " + event.getOrderId() + " 已成功下单，总价："
                        + event.getTotalCents() / 100.0 + " " + event.getCurrency()));
    }
}
