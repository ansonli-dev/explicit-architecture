package com.example.order.domain.ports;

import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Write-side repository port — domain concept: "the collection of all Orders". */
public interface OrderPersistence {

    CancellationResult cancelOwned(OrderId id, CustomerId owner, String reason, long expectedVersion);
    Order save(Order order);

    Optional<Order> findById(OrderId id);

    record Cancellation(UUID orderId, String reason, Instant cancelledAt) {}
    record CancellationResult(Cancellation cancellation, boolean created) {}
}
