package com.example.order.application.port.outbound;

import com.example.order.application.query.order.OrderDetailResponse;
import com.example.order.application.query.order.OrderItemResponse;
import com.example.order.application.query.order.OrderResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderSearchRepository {

    // --- Read ---

    Optional<OrderDetailResponse> findById(UUID orderId);

    List<OrderResponse> findByCustomerIdAndStatus(UUID customerId, String status, int page, int size);

    // --- Write (projection side) ---

    void save(OrderProjection projection);

    void updateStatus(UUID orderId, String status);

    void updateStatusWithTracking(UUID orderId, String status, String trackingNumber);

    void updateStatusWithReason(UUID orderId, String status, String cancelReason);

    record OrderProjection(
            UUID orderId,
            UUID customerId,
            String customerEmail,
            String status,
            long totalCents,
            String currency,
            List<OrderItemResponse> items
    ) {}
}
