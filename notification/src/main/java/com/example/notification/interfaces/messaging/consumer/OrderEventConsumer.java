package com.example.notification.interfaces.messaging.consumer;

import com.example.events.KafkaResourceConstants;
import com.example.events.v1.OrderCancelled;
import com.example.events.v1.OrderConfirmed;
import com.example.events.v1.OrderPlaced;
import com.example.events.v1.OrderShipped;
import com.example.seedwork.infrastructure.kafka.KafkaMessageProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Routes incoming order events to their respective {@link com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler}.
 * No business logic here — idempotency, retry persistence, and offset commit are all
 * handled by {@link KafkaMessageProcessor}.
 */
@Component
public class OrderEventConsumer {

    private final KafkaMessageProcessor processor;
    private final OrderPlacedHandler orderPlacedHandler;
    private final OrderCancelledHandler orderCancelledHandler;
    private final OrderConfirmedHandler orderConfirmedHandler;
    private final OrderShippedHandler orderShippedHandler;

    public OrderEventConsumer(KafkaMessageProcessor processor,
                               OrderPlacedHandler orderPlacedHandler,
                               OrderCancelledHandler orderCancelledHandler,
                               OrderConfirmedHandler orderConfirmedHandler,
                               OrderShippedHandler orderShippedHandler) {
        this.processor = processor;
        this.orderPlacedHandler = orderPlacedHandler;
        this.orderCancelledHandler = orderCancelledHandler;
        this.orderConfirmedHandler = orderConfirmedHandler;
        this.orderShippedHandler = orderShippedHandler;
    }

    @KafkaListener(topics = KafkaResourceConstants.TOPIC_ORDER_PLACED, groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderPlaced(ConsumerRecord<String, OrderPlaced> record, Acknowledgment ack) {
        processor.process(orderPlacedHandler, record, ack);
    }

    @KafkaListener(topics = KafkaResourceConstants.TOPIC_ORDER_CANCELLED, groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCancelled(ConsumerRecord<String, OrderCancelled> record, Acknowledgment ack) {
        processor.process(orderCancelledHandler, record, ack);
    }

    @KafkaListener(topics = KafkaResourceConstants.TOPIC_ORDER_CONFIRMED, groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderConfirmed(ConsumerRecord<String, OrderConfirmed> record, Acknowledgment ack) {
        processor.process(orderConfirmedHandler, record, ack);
    }

    @KafkaListener(topics = KafkaResourceConstants.TOPIC_ORDER_SHIPPED, groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderShipped(ConsumerRecord<String, OrderShipped> record, Acknowledgment ack) {
        processor.process(orderShippedHandler, record, ack);
    }
}
