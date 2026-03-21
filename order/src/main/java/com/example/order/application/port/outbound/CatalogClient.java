package com.example.order.application.port.outbound;


import java.util.UUID;

/**
 * Secondary port — outbound calls to the catalog service.
 * <p>
 * Implemented by {@code infrastructure/client/CatalogRestClient}.
 * The application layer declares what it needs; infrastructure decides how to deliver it.
 */
public interface CatalogClient {
    StockAvailability checkStock(UUID bookId);

    void reserveStock(UUID bookId, UUID orderId, int quantity);
}
