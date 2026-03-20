package com.example.order.application.command.order;

import com.example.seedwork.application.command.CommandHandler;
import com.example.order.application.port.outbound.CatalogClient;
import com.example.order.application.port.outbound.OrderPersistence;
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
    private final CatalogClient catalogClient;

    @Override
    public Void handle(CancelOrderCommand command) {
        log.info("Cancelling order: orderId={}, reason={}", command.orderId(), command.reason());
        Order order = orderRepository.findById(OrderId.of(command.orderId()))
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));

        // cancel() registers OrderCancelled into the aggregate's event list.
        // orderRepository.save() will pull it and write the outbox entry atomically.
        order.cancel(command.reason());
        orderRepository.save(order);

        order.getItems()
                .forEach(item -> catalogClient.releaseStock(item.bookId(), command.orderId(), item.quantity()));

        log.info("Order cancelled: orderId={}", command.orderId());
        return null;
    }
}
