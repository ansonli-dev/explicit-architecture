package com.example.events;

/**
 * Centrally managed Kafka resource constants.
 * This class provides type-safe access to topic names and service identifiers
 * used for ACL management.
 */
public final class KafkaResourceConstants {

    // --- Topic Names ---

    public static final String TOPIC_ORDER_PLACED = "bookstore.order.placed";
    public static final String TOPIC_ORDER_CONFIRMED = "bookstore.order.confirmed";
    public static final String TOPIC_ORDER_CANCELLED = "bookstore.order.cancelled";
    public static final String TOPIC_ORDER_SHIPPED = "bookstore.order.shipped";

    public static final String TOPIC_STOCK_RESERVED = "bookstore.stock.reserved";
    public static final String TOPIC_STOCK_RELEASED = "bookstore.stock.released";

    // --- Service Principals (for ACLs) ---

    public static final String SERVICE_ORDER = "order";
    public static final String SERVICE_CATALOG = "catalog";
    public static final String SERVICE_NOTIFICATION = "notification";
    public static final String SERVICE_ORDER_READ_MODEL = "order-read-model";

    private KafkaResourceConstants() {
        // Prevent instantiation
    }
}
