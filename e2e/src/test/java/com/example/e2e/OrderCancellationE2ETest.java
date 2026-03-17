package com.example.e2e;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests for the order cancellation flow:
 *
 * <pre>
 * Client → PUT /api/v1/orders/{id}/cancel (order-service)
 *              → order.cancel() registers OrderCancelled domain event
 *              → save Order (status = CANCELLED) to PostgreSQL
 *              → outbox row written atomically
 *              → CatalogClient.releaseStock() [sync HTTP to catalog-service]
 *          ← 204 No Content
 *
 * Debezium reads WAL → publishes OrderCancelled to Kafka
 * order-service consumer → updates OrderElasticDocument status
 *
 * Assertions:
 *   - availableStock restored (sync, immediately after 204)
 *   - order status CANCELLED in read model (async, Awaitility up to 15 s)
 * </pre>
 */
class OrderCancellationE2ETest extends BaseE2ETest {

    @Test
    void givenPlacedOrder_whenCancel_thenStockRestoredAndStatusCancelledInReadModel() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        UUID bookId = createBook("Microservices Patterns", 10, 7500);
        UUID orderId = placeOrder(customerId, "cancel@example.com", bookId, "Microservices Patterns", 7500, 4);
        assertThat(getAvailableStock(bookId)).isEqualTo(6);  // 10 - 4

        // Act
        cancelOrder(orderId, "Customer changed mind");

        // Assert — stock released synchronously (catalog called inline by order service)
        assertThat(getAvailableStock(bookId)).isEqualTo(10);

        // Assert — order status CANCELLED in read model
        await().atMost(15, SECONDS)
                .untilAsserted(() -> {
                    var response = getOrderFromReadModel(orderId);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.<String>path("status")).isEqualTo("CANCELLED");
                });
    }

    @Test
    void givenCancelledOrder_stockCanBeReservedByAnotherOrder() {
        // Arrange
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID bookId = createBook("CQRS Journey", 3, 5500);

        // Customer A places and then cancels
        UUID orderA = placeOrder(customerA, "a@example.com", bookId, "CQRS Journey", 5500, 3);
        assertThat(getAvailableStock(bookId)).isEqualTo(0);
        cancelOrder(orderA, "No longer needed");
        assertThat(getAvailableStock(bookId)).isEqualTo(3);  // fully restored

        // Customer B can now place an order for the same stock
        UUID orderB = placeOrder(customerB, "b@example.com", bookId, "CQRS Journey", 5500, 2);
        assertThat(getAvailableStock(bookId)).isEqualTo(1);
        assertThat(orderB).isNotNull();
    }
}
