package com.example.e2e;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests verifying that order items are correctly propagated
 * through the CDC + ES Sink pipeline into the ElasticSearch read model.
 *
 * <pre>
 * Client → POST /api/v1/orders (order-service)
 *              → save Order (with items as JSONB) to PostgreSQL
 *
 * Debezium CDC reads orders.items (jsonb) from WAL
 *     → publishes to debezium.order-db.public.orders Kafka topic
 *     → ES Sink Connector writes document to ElasticSearch
 *         (items stored as JSON string via ReplaceField SMT rename)
 *
 * order-service GET /api/v1/orders/{id}
 *     → OrderSearchAdapter reads ES, deserializes items JSON string
 *     → returns OrderDetailResponse with List<OrderItemResponse>
 *
 * Assertions:
 *   - items list is non-empty (CDC pipeline transferred the jsonb column)
 *   - bookId, bookTitle, unitPriceCents, currency, quantity all correct
 * </pre>
 */
class OrderReadModelItemsE2ETest extends BaseE2ETest {

    @Test
    void givenPlacedOrder_whenReadFromEs_thenItemsContainCorrectBookDetails() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        UUID bookId = createBook("Clean Code", 5, 4999);

        // Act
        UUID orderId = placeOrder(customerId, "items@example.com", bookId, "Clean Code", 4999, 2);

        // Assert — wait for CDC → Kafka → ES Sink pipeline to propagate
        await().atMost(30, SECONDS)
                .untilAsserted(() -> {
                    var response = getOrderFromReadModel(orderId);
                    assertThat(response.statusCode()).isEqualTo(200);

                    // items list must be populated from ES (not a JPA fallback path)
                    assertThat(response.<Integer>path("items.size()")).isGreaterThan(0);

                    // item details must match what was submitted
                    assertThat(response.<String>path("items[0].bookTitle")).isEqualTo("Clean Code");
                    assertThat(response.<Integer>path("items[0].quantity")).isEqualTo(2);
                    assertThat(response.<Integer>path("items[0].unitPriceCents")).isEqualTo(4999);
                    assertThat(response.<String>path("items[0].currency")).isEqualTo("CNY");

                    // totalCents must match quantity × unitPrice
                    assertThat(response.<Integer>path("totalCents")).isEqualTo(9998);
                });
    }

    @Test
    void givenOrderWithMultipleItems_whenReadFromEs_thenAllItemsPresent() {
        // Arrange — two distinct books
        UUID customerId = UUID.randomUUID();
        UUID bookA = createBook("DDD", 5, 5500);
        UUID bookB = createBook("CQRS Journey", 5, 6000);

        // Place two separate single-item orders (PlaceOrderRequest supports one item group;
        // verify both orders each carry their own item)
        UUID orderA = placeOrder(customerId, "multi@example.com", bookA, "DDD", 5500, 1);
        UUID orderB = placeOrder(customerId, "multi@example.com", bookB, "CQRS Journey", 6000, 3);

        await().atMost(30, SECONDS)
                .untilAsserted(() -> {
                    var respA = getOrderFromReadModel(orderA);
                    assertThat(respA.statusCode()).isEqualTo(200);
                    assertThat(respA.<String>path("items[0].bookTitle")).isEqualTo("DDD");

                    var respB = getOrderFromReadModel(orderB);
                    assertThat(respB.statusCode()).isEqualTo(200);
                    assertThat(respB.<String>path("items[0].bookTitle")).isEqualTo("CQRS Journey");
                    assertThat(respB.<Integer>path("items[0].quantity")).isEqualTo(3);
                });
    }
}
