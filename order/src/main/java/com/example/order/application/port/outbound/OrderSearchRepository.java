package com.example.order.application.port.outbound;

import com.example.order.application.query.order.OrderDetailResult;
import com.example.order.application.query.order.OrderSummaryResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only port for the Elasticsearch order read model.
 * Write side is handled by the Kafka Connect Elasticsearch Sink Connector
 * (CDC from the orders table via Debezium) — no application code writes to ES.
 */
public interface OrderSearchRepository {

    Optional<OrderDetailResult> findById(UUID orderId);

    List<OrderSummaryResult> findByCustomerIdAndStatus(UUID customerId, String status, int page, int size);
}
