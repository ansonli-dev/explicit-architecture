package com.example.order.contract;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.example.order.application.query.order.StockCheckResponse;
import com.example.order.infrastructure.client.CatalogRestClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.UUID;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * BDCT Consumer-side Pact test: order-service → catalog-service.
 * <p>
 * Each {@code @Pact} method defines one interaction (what order expects from catalog).
 * Pact runs a mock server, executes the real {@link CatalogRestClient} against it, and
 * records the interaction in {@code build/pacts/order-service-catalog-service.json}.
 * <p>
 * The generated pact file is published to PactFlow in CI. PactFlow then cross-validates
 * it against catalog's OpenAPI spec — no provider-side replay needed (BDCT).
 */
@Tag("contract")
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "catalog-service")
class CatalogClientPactTest {

    private static final UUID BOOK_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    // ─── checkStock ───────────────────────────────────────────────────────────

    @Pact(consumer = "order-service")
    RequestResponsePact checkStockPact(PactDslWithProvider builder) {
        return builder
                .given("book exists with available stock")
                .uponReceiving("GET stock for an existing book")
                    .method("GET")
                    .path("/api/v1/books/" + BOOK_ID + "/stock")
                .willRespondWith()
                    .status(200)
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(newJsonBody(b -> {
                        b.uuid("bookId", BOOK_ID);
                        b.integerType("availableStock", 100);
                    }).build())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "checkStockPact")
    void checkStock_parsesBookIdAndAvailableStock(MockServer mockServer) {
        CatalogRestClient client = new CatalogRestClient(mockServer.getUrl());
        StockCheckResponse response = client.checkStock(BOOK_ID);
        assertThat(response.bookId()).isNotNull();
        assertThat(response.availableStock()).isGreaterThanOrEqualTo(0);
    }

    // ─── reserveStock ─────────────────────────────────────────────────────────

    @Pact(consumer = "order-service")
    RequestResponsePact reserveStockPact(PactDslWithProvider builder) {
        return builder
                .given("book exists with available stock")
                .uponReceiving("POST to reserve stock for an order")
                    .method("POST")
                    .path("/api/v1/books/" + BOOK_ID + "/stock/reserve")
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(newJsonBody(b -> {
                        b.uuid("orderId", ORDER_ID);
                        b.integerType("quantity", 2);
                    }).build())
                .willRespondWith()
                    .status(200)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "reserveStockPact")
    void reserveStock_completesWithoutError(MockServer mockServer) {
        CatalogRestClient client = new CatalogRestClient(mockServer.getUrl());
        assertThatNoException().isThrownBy(
                () -> client.reserveStock(BOOK_ID, ORDER_ID, 2));
    }

    // ─── releaseStock ─────────────────────────────────────────────────────────

    @Pact(consumer = "order-service")
    RequestResponsePact releaseStockPact(PactDslWithProvider builder) {
        return builder
                .given("book has reserved stock for the order")
                .uponReceiving("POST to release stock for an order")
                    .method("POST")
                    .path("/api/v1/books/" + BOOK_ID + "/stock/release")
                    .headers(Map.of("Content-Type", "application/json"))
                    .body(newJsonBody(b -> {
                        b.uuid("orderId", ORDER_ID);
                        b.integerType("quantity", 2);
                    }).build())
                .willRespondWith()
                    .status(204)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "releaseStockPact")
    void releaseStock_completesWithoutError(MockServer mockServer) {
        CatalogRestClient client = new CatalogRestClient(mockServer.getUrl());
        assertThatNoException().isThrownBy(
                () -> client.releaseStock(BOOK_ID, ORDER_ID, 2));
    }
}
