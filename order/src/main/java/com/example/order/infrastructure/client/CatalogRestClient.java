package com.example.order.infrastructure.client;

import com.example.order.application.port.outbound.CatalogClient;
import com.example.order.application.port.outbound.StockAvailability;
import com.example.order.infrastructure.client.CatalogHttpClient.ReleaseStockRequest;
import com.example.order.infrastructure.client.CatalogHttpClient.ReserveStockRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Secondary adapter — implements {@link CatalogClient} via HTTP.
 * Delegates to the declarative {@link CatalogHttpClient} proxy (Spring HTTP Interface).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogRestClient implements CatalogClient {

    private final CatalogHttpClient catalogHttpClient;

    @Override
    public StockAvailability checkStock(UUID bookId) {
        return catalogHttpClient.checkStock(bookId);
    }

    @Override
    public void reserveStock(UUID bookId, UUID orderId, int quantity) {
        log.info("Reserving stock: bookId={}, qty={}", bookId, quantity);
        catalogHttpClient.reserveStock(bookId, new ReserveStockRequest(orderId, quantity));
    }

    @Override
    public void releaseStock(UUID bookId, UUID orderId, int quantity) {
        log.info("Releasing stock: bookId={}, qty={}", bookId, quantity);
        catalogHttpClient.releaseStock(bookId, new ReleaseStockRequest(orderId, quantity));
    }
}
