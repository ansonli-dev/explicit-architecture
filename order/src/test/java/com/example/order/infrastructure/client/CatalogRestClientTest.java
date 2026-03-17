package com.example.order.infrastructure.client;

import com.example.order.application.query.order.StockCheckResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRestClientTest {

    private MockWebServer mockWebServer;
    private CatalogRestClient catalogRestClient;

    private final UUID bookId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString();
        catalogRestClient = new CatalogRestClient(baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void givenBookWithAvailableStock_whenCheckStock_thenStockCheckResponseReturned() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {"bookId":"%s","available":95}
                        """.formatted(bookId))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        StockCheckResponse response = catalogRestClient.checkStock(bookId);

        // Assert
        assertThat(response.bookId()).isEqualTo(bookId);
        assertThat(response.availableStock()).isEqualTo(95);

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/books/" + bookId + "/stock");
        assertThat(request.getMethod()).isEqualTo("GET");
    }

    @Test
    void givenReserveStockRequest_whenReserveStock_thenCorrectRequestSentToCatalog() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        // Act
        catalogRestClient.reserveStock(bookId, orderId, 3);

        // Assert
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/books/" + bookId + "/stock/reserve");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().readUtf8()).contains("3");
    }

    @Test
    void givenReleaseStockRequest_whenReleaseStock_thenCorrectRequestSentToCatalog() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        // Act
        catalogRestClient.releaseStock(bookId, orderId, 2);

        // Assert
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/api/v1/books/" + bookId + "/stock/release");
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().readUtf8()).contains("2");
    }
}
