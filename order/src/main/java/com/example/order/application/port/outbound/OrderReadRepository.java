package com.example.order.application.port.outbound;

import com.example.order.application.query.order.OrderDetailResponse;
import com.example.order.domain.model.OrderId;

import java.util.Optional;

/**
 * Read-side port for querying order projections directly from the write DB.
 * Used as ES fallback in GetOrderQueryHandler.
 * Lives in the application layer (not domain) because it returns an application DTO.
 */
public interface OrderReadRepository {
    Optional<OrderDetailResponse> findDetailById(OrderId id);
}
