package com.example.e2e;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end tests for the order placement flow:
 *
 * <pre>
 * Client → POST /api/v1/orders (order-service)
 *              → CatalogClient.reserveStock() [sync HTTP to catalog-service]
 *              → save Order to PostgreSQL
 *              → outbox row written atomically
 *          ← 201 Created
 *
 * Debezium reads WAL → publishes OrderPlaced to Kafka
 * order-service OrderPlacedConsumer → writes OrderElasticDocument to ElasticSearch
 *
 * Assertions:
 *   - availableStock reduced (sync, immediately after 201)
 *   - order visible in read model (async, Awaitility up to 15 s)
 * </pre>
 */
class OrderPlacementE2ETest extends BaseE2ETest {

    @Test
    void givenAvailableStock_whenPlaceOrder_thenStockReducedAndOrderAppearsInReadModel() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        UUID bookId = createBook("Clean Code", 10, 5000);

        // Act
        UUID orderId = placeOrder(customerId, "buyer@example.com", bookId, "Clean Code", 5000, 3);

        // Assert — stock is reserved synchronously (catalog called inline by order service)
        assertThat(getAvailableStock(bookId)).isEqualTo(7);

        // Assert — order appears in ElasticSearch read model (eventually consistent via Kafka)
        await().atMost(15, SECONDS)
                .untilAsserted(() -> {
                    var response = getOrderFromReadModel(orderId);
                    assertThat(response.statusCode()).isEqualTo(200);
                    assertThat(response.<String>path("status")).isEqualTo("PENDING");
                    assertThat(response.<String>path("orderId")).isEqualTo(orderId.toString());
                });
    }

    @Test
    void givenInsufficientStock_whenPlaceOrder_thenReturns4xxAndStockUnchanged() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        UUID bookId = createBook("DDD", 2, 4500);

        // Act & Assert — order rejected due to insufficient stock
        given().baseUri(E2EConfig.ORDER_BASE_URL)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "customerId", customerId,
                        "customerEmail", "buyer@example.com",
                        "items", List.of(Map.of(
                                "bookId", bookId,
                                "bookTitle", "DDD",
                                "unitPriceCents", 4500,
                                "currency", "CNY",
                                "quantity", 5))))   // more than available
                .post("/api/v1/orders")
                .then().statusCode(org.hamcrest.Matchers.greaterThanOrEqualTo(400));

        // Stock must not change
        assertThat(getAvailableStock(bookId)).isEqualTo(2);
    }

    @Test
    void givenMultipleOrders_eachReducesStockIndependently() {
        // Arrange
        UUID customerA = UUID.randomUUID();
        UUID customerB = UUID.randomUUID();
        UUID bookId = createBook("Refactoring", 10, 6000);

        // Act
        placeOrder(customerA, "a@example.com", bookId, "Refactoring", 6000, 2);
        placeOrder(customerB, "b@example.com", bookId, "Refactoring", 6000, 3);

        // Assert — both reservations applied
        assertThat(getAvailableStock(bookId)).isEqualTo(5);
    }
}
