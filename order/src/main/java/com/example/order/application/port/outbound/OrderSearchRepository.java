package com.example.order.application.port.outbound;

import com.example.order.application.query.order.OrderDetailView;
import com.example.order.application.query.order.OrderSummaryView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only port for the Elasticsearch order read model.
 * Write side is handled by the Kafka Connect Elasticsearch Sink Connector
 * (CDC from the orders table via Debezium) — no application code writes to ES.
 */
public interface OrderSearchRepository {

    Optional<OrderDetailView> findById(UUID orderId);

    List<OrderSummaryView> findByCustomerIdAndStatus(UUID customerId, String status, int page, int size);
}
