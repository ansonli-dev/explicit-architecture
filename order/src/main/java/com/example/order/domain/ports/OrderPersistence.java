package com.example.order.domain.ports;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;

import java.util.Optional;

/** Write-side repository port — domain concept: "the collection of all Orders". */
public interface OrderPersistence {
    Order save(Order order);

    Optional<Order> findById(OrderId id);
}
