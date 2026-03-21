package com.example.order.infrastructure.client;

import com.example.order.application.port.outbound.CatalogClient;
import com.example.order.application.port.outbound.StockAvailability;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

/**
 * Secondary adapter — implements {@link CatalogClient} via HTTP.
 * Synchronous WebClient calls to the catalog-service REST API.
 */
@Slf4j
@Component
public class CatalogRestClient implements CatalogClient {

    private final WebClient webClient;

    public CatalogRestClient(@Value("${services.catalog.base-url}") String catalogBaseUrl) {
        this.webClient = WebClient.builder().baseUrl(catalogBaseUrl).build();
    }

    @Override
    public StockAvailability checkStock(UUID bookId) {
        return webClient.get()
                .uri("/api/v1/books/{id}/stock", bookId)
                .retrieve()
                .bodyToMono(StockAvailability.class)
                .block();
    }

    @Override
    public void reserveStock(UUID bookId, UUID orderId, int quantity) {
        log.info("Reserving stock: bookId={}, qty={}", bookId, quantity);
        webClient.post()
                .uri("/api/v1/books/{id}/stock/reserve", bookId)
                .bodyValue(new ReserveRequest(orderId, quantity))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    record ReserveRequest(UUID orderId, int quantity) {}
}
