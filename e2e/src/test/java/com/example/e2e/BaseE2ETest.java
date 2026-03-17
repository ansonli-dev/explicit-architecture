package com.example.e2e;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Shared HTTP helpers for all e2e tests.
 *
 * <p>Tests only communicate via public REST APIs — no direct DB access.
 * Each test creates isolated data using random UUIDs; no cleanup is needed
 * because tests never depend on each other's state.
 */
abstract class BaseE2ETest {

    // ── Catalog helpers ───────────────────────────────────────────────────────

    protected UUID createBook(String title, int initialStock, long priceCents) {
        return UUID.fromString(
                given().baseUri(E2EConfig.CATALOG_BASE_URL)
                        .contentType(ContentType.JSON)
                        .body(Map.of(
                                "title", title,
                                "authorName", "E2E Author",
                                "authorBiography", "Created by e2e test",
                                "priceCents", priceCents,
                                "currency", "CNY",
                                "categoryName", "E2E",
                                "initialStock", initialStock))
                        .post("/api/v1/books")
                        .then().statusCode(201)
                        .extract().path("id"));
    }

    protected int getAvailableStock(UUID bookId) {
        return given().baseUri(E2EConfig.CATALOG_BASE_URL)
                .get("/api/v1/books/{id}/stock", bookId)
                .then().statusCode(200)
                .extract().path("availableStock");
    }

    // ── Order helpers ─────────────────────────────────────────────────────────

    protected UUID placeOrder(UUID customerId, String customerEmail,
                              UUID bookId, String bookTitle, long unitPriceCents, int quantity) {
        return UUID.fromString(
                given().baseUri(E2EConfig.ORDER_BASE_URL)
                        .contentType(ContentType.JSON)
                        .body(Map.of(
                                "customerId", customerId,
                                "customerEmail", customerEmail,
                                "items", List.of(Map.of(
                                        "bookId", bookId,
                                        "bookTitle", bookTitle,
                                        "unitPriceCents", unitPriceCents,
                                        "currency", "CNY",
                                        "quantity", quantity))))
                        .post("/api/v1/orders")
                        .then().statusCode(201)
                        .extract().path("orderId"));
    }

    protected void cancelOrder(UUID orderId, String reason) {
        given().baseUri(E2EConfig.ORDER_BASE_URL)
                .contentType(ContentType.JSON)
                .body(Map.of("reason", reason))
                .put("/api/v1/orders/{id}/cancel", orderId)
                .then().statusCode(204);
    }

    /**
     * Reads the order from the ElasticSearch read model.
     * May return 404 briefly while Kafka projection catches up — callers should use Awaitility.
     */
    protected Response getOrderFromReadModel(UUID orderId) {
        return given().baseUri(E2EConfig.ORDER_BASE_URL)
                .get("/api/v1/orders/{id}", orderId)
                .then().extract().response();
    }
}
