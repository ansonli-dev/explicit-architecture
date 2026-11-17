package com.example.catalog.interfaces.messaging.consumer;

import com.example.events.KafkaResourceConstants;
import com.example.events.v1.OrderCancelled;
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
    private final OrderCancelledHandler orderCancelledHandler;

    public OrderEventConsumer(KafkaMessageProcessor processor,
                              OrderCancelledHandler orderCancelledHandler) {
        this.processor = processor;
        this.orderCancelledHandler = orderCancelledHandler;
    }

    @KafkaListener(topics = KafkaResourceConstants.TOPIC_ORDER_CANCELLED, groupId = "${spring.kafka.consumer.group-id}")
    public void onOrderCancelled(ConsumerRecord<String, OrderCancelled> record, Acknowledgment ack) {
        var event = record.value();
        if (event.getReservationProtocol() == 1) {
            processor.process(orderCancelledHandler, record, ack);
        } else {
            // A v2 (or unsupported) protocol never enters the legacy retry/ack route.
            processor.processSimple(record, ack, orderCancelledHandler.eventId(event),
                    () -> orderCancelledHandler.handle(event));
        }
    }
}
