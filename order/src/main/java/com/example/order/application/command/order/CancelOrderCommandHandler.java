package com.example.order.application.command.order;

import com.example.seedwork.application.command.CommandHandler;
import com.example.order.domain.ports.OrderPersistence;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.order.application.query.order.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancelOrderCommandHandler implements CommandHandler<CancelOrderCommand, Void> {

    private final OrderPersistence orderRepository;

    @Override
    public Void handle(CancelOrderCommand command) {
        log.info("Cancelling order: orderId={}, reason={}", command.orderId(), command.reason());
        Order order = orderRepository.findById(OrderId.of(command.orderId()))
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        order.cancel(command.reason());
        orderRepository.save(order);
        // Stock release is event-driven: OrderCancelled domain event → outbox → Kafka → catalog releases stock

        log.info("Order cancelled: orderId={}", command.orderId());
        return null;
    }
}
