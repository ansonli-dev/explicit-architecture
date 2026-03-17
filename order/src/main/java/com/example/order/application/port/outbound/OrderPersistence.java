package com.example.order.application.port.outbound;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;

import java.util.Optional;

/** Secondary port — JPA write-side repository. */
public interface OrderPersistence {
    Order save(Order order);

    Optional<Order> findById(OrderId id);
}
