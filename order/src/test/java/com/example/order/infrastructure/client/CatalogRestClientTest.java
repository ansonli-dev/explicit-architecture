package com.example.order.infrastructure.client;

import com.example.order.application.port.outbound.StockAvailability;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

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

        RestClient restClient = RestClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        CatalogHttpClient httpClient = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(CatalogHttpClient.class);

        catalogRestClient = new CatalogRestClient(httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void givenBookWithAvailableStock_whenCheckStock_thenStockAvailabilityReturned() throws Exception {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
                .setBody("""
                        {"bookId":"%s","availableStock":95}
                        """.formatted(bookId))
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        StockAvailability response = catalogRestClient.checkStock(bookId);

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

}
