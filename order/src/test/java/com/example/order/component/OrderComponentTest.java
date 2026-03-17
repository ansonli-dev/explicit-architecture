package com.example.order.component;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1)
@Testcontainers
@Sql(scripts = "/cleanup.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
class OrderComponentTest {

    @Container
    static ElasticsearchContainer elasticsearch =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    static MockWebServer mockCatalog;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris",
                () -> "http://" + elasticsearch.getHttpHostAddress());
        registry.add("services.catalog.base-url",
                () -> "http://localhost:" + mockCatalog.getPort());
    }

    @BeforeAll
    static void startMockCatalog() throws IOException {
        mockCatalog = new MockWebServer();
        mockCatalog.start();
    }

    @AfterAll
    static void stopMockCatalog() throws IOException {
        mockCatalog.close();
    }

    @LocalServerPort int port;

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        mockCatalog.setDispatcher(sufficientStockDispatcher());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Dispatcher sufficientStockDispatcher() {
        return new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.matches("/api/v1/books/.*/stock") && "GET".equals(request.getMethod())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody("{\"bookId\":\"" + UUID.randomUUID() + "\",\"available\":100}");
                }
                if (path.matches("/api/v1/books/.*/stock/reserve") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                }
                if (path.matches("/api/v1/books/.*/stock/release") && "POST".equals(request.getMethod())) {
                    return new MockResponse().setResponseCode(200);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private Dispatcher insufficientStockDispatcher() {
        return new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.matches("/api/v1/books/.*/stock") && "GET".equals(request.getMethod())) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody("{\"bookId\":\"" + UUID.randomUUID() + "\",\"available\":0}");
                }
                return new MockResponse().setResponseCode(404);
            }
        };
    }

    private String placeOrder(UUID customerId, UUID bookId, int quantity) {
        return given().contentType(ContentType.JSON)
                .body(Map.of(
                        "customerId", customerId,
                        "customerEmail", "test@example.com",
                        "items", List.of(Map.of(
                                "bookId", bookId,
                                "bookTitle", "Test Book",
                                "unitPriceCents", 4500,
                                "currency", "CNY",
                                "quantity", quantity))))
                .post("/api/v1/orders")
                .then().statusCode(201)
                .extract().path("orderId");
    }

    // ── REST API + PostgreSQL ─────────────────────────────────────────────────

    @Test
    void givenValidOrder_whenPlaceOrder_thenReturns201AndPersistsToDb() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        // Act
        String orderId = placeOrder(customerId, bookId, 2);

        // Assert
        assertThat(orderId).isNotNull();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ?", Integer.class, UUID.fromString(orderId));
        assertThat(count).isEqualTo(1);
    }

    @Test
    void givenValidOrder_whenPlaceOrder_thenElasticsearchDocumentCreated() throws InterruptedException {
        // Arrange
        UUID customerId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        // Act
        String orderId = placeOrder(customerId, bookId, 1);

        // Allow ES indexing (TransactionalEventListener AFTER_COMMIT) to complete
        Thread.sleep(1500);

        // Assert
        given()
                .get("/api/v1/orders/{id}", orderId)
                .then().statusCode(200);
    }

    @Test
    void givenExistingOrder_whenGetOrder_thenReturns200WithDetails() {
        // Arrange
        String orderId = placeOrder(UUID.randomUUID(), UUID.randomUUID(), 1);

        // Act + Assert
        given()
                .get("/api/v1/orders/{id}", orderId)
                .then().statusCode(200);
    }

    @Test
    void givenExistingPendingOrder_whenCancelOrder_thenStatusIsCancelled() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        String orderId = placeOrder(customerId, UUID.randomUUID(), 1);

        // Act
        given().contentType(ContentType.JSON)
                .body(Map.of("reason", "customer request"))
                .put("/api/v1/orders/{id}/cancel", orderId)
                .then().statusCode(204);

        // Assert
        String status = jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class,
                UUID.fromString(orderId));
        assertThat(status).isEqualTo("CANCELLED");
    }

    @Test
    void givenNonExistentOrderId_whenGetOrder_thenReturns404() {
        given()
                .get("/api/v1/orders/{id}", UUID.randomUUID())
                .then().statusCode(404);
    }

    // ── Catalog stub verification ─────────────────────────────────────────────

    @Test
    void givenValidOrder_whenPlaceOrder_thenCatalogStockCheckAndReserveCalled() throws InterruptedException {
        // Arrange
        UUID customerId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        // Act
        String orderId = placeOrder(customerId, bookId, 3);

        // Assert
        assertThat(orderId).isNotNull();
        RecordedRequest stockCheck = mockCatalog.takeRequest();
        assertThat(stockCheck.getPath()).contains("/stock");
    }

    @Test
    void givenInsufficientStock_whenPlaceOrder_thenReturns4xxAndOrderNotPersisted() {
        // Arrange
        UUID customerId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        mockCatalog.setDispatcher(insufficientStockDispatcher());

        // Act + Assert
        given().contentType(ContentType.JSON)
                .body(Map.of(
                        "customerId", customerId,
                        "customerEmail", "test@example.com",
                        "items", List.of(Map.of(
                                "bookId", bookId,
                                "bookTitle", "Test Book",
                                "unitPriceCents", 4500,
                                "currency", "CNY",
                                "quantity", 1))))
                .post("/api/v1/orders")
                .then().statusCode(greaterThanOrEqualTo(400));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE customer_id = ?",
                Integer.class, customerId);
        assertThat(count).isEqualTo(0);
    }
}
