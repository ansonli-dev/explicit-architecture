package com.example.order.infrastructure.client;

import com.example.order.application.port.outbound.StockAvailability;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.UUID;

/**
 * Declarative HTTP client for the catalog-service REST API.
 * <p>
 * Spring creates a proxy at runtime via {@link org.springframework.web.client.support.RestClientAdapter}
 * and {@link org.springframework.web.service.invoker.HttpServiceProxyFactory}.
 */
@HttpExchange("/api/v1/books")
interface CatalogHttpClient {

    @GetExchange("/{bookId}/stock")
    StockAvailability checkStock(@PathVariable UUID bookId);

    @PostExchange("/{bookId}/stock/reserve")
    void reserveStock(@PathVariable UUID bookId, @RequestBody ReserveStockRequest request);

    @PostExchange("/{bookId}/stock/release")
    void releaseStock(@PathVariable UUID bookId, @RequestBody ReleaseStockRequest request);

    record ReserveStockRequest(UUID orderId, int quantity) {}
    record ReleaseStockRequest(UUID orderId, int quantity) {}
}
