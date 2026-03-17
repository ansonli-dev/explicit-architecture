package com.example.notification.interfaces.messaging.consumer;

import com.example.events.v1.OrderCancelled;
import com.example.events.v1.OrderConfirmed;
import com.example.events.v1.OrderPlaced;
import com.example.events.v1.OrderShipped;
import com.example.seedwork.infrastructure.kafka.KafkaMessageProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock KafkaMessageProcessor processor;
    @Mock OrderPlacedHandler orderPlacedHandler;
    @Mock OrderCancelledHandler orderCancelledHandler;
    @Mock OrderConfirmedHandler orderConfirmedHandler;
    @Mock OrderShippedHandler orderShippedHandler;
    @Mock Acknowledgment ack;

    private OrderEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderEventConsumer(
                processor, orderPlacedHandler, orderCancelledHandler, orderConfirmedHandler, orderShippedHandler);
    }

    @Test
    void givenOrderPlacedRecord_whenOnOrderPlaced_thenProcessorCalledWithOrderPlacedHandler() {
        // Arrange
        ConsumerRecord<String, OrderPlaced> record = new ConsumerRecord<>("order.placed", 0, 0L, "key", null);

        // Act
        consumer.onOrderPlaced(record, ack);

        // Assert
        verify(processor).process(orderPlacedHandler, record, ack);
    }

    @Test
    void givenOrderCancelledRecord_whenOnOrderCancelled_thenProcessorCalledWithOrderCancelledHandler() {
        // Arrange
        ConsumerRecord<String, OrderCancelled> record = new ConsumerRecord<>("order.cancelled", 0, 0L, "key", null);

        // Act
        consumer.onOrderCancelled(record, ack);

        // Assert
        verify(processor).process(orderCancelledHandler, record, ack);
    }

    @Test
    void givenOrderConfirmedRecord_whenOnOrderConfirmed_thenProcessorCalledWithOrderConfirmedHandler() {
        // Arrange
        ConsumerRecord<String, OrderConfirmed> record = new ConsumerRecord<>("order.confirmed", 0, 0L, "key", null);

        // Act
        consumer.onOrderConfirmed(record, ack);

        // Assert
        verify(processor).process(orderConfirmedHandler, record, ack);
    }

    @Test
    void givenOrderShippedRecord_whenOnOrderShipped_thenProcessorCalledWithOrderShippedHandler() {
        // Arrange
        ConsumerRecord<String, OrderShipped> record = new ConsumerRecord<>("order.shipped", 0, 0L, "key", null);

        // Act
        consumer.onOrderShipped(record, ack);

        // Assert
        verify(processor).process(orderShippedHandler, record, ack);
    }
}
