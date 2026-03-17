package com.example.e2e;

/**
 * Base URLs for the three services under test.
 *
 * <p>Values are resolved in priority order:
 * <ol>
 *   <li>Gradle project property: {@code ./gradlew test -Pe2e.catalog.base-url=http://staging:8081}</li>
 *   <li>Environment variable:    {@code CATALOG_BASE_URL=http://staging:8081}</li>
 *   <li>Default:                 {@code http://localhost:<port>} for local development</li>
 * </ol>
 */
public final class E2EConfig {

    public static final String CATALOG_BASE_URL =
            System.getProperty("e2e.catalog.base-url", "http://localhost:8081");

    public static final String ORDER_BASE_URL =
            System.getProperty("e2e.order.base-url", "http://localhost:8082");

    public static final String NOTIFICATION_BASE_URL =
            System.getProperty("e2e.notification.base-url", "http://localhost:8083");

    private E2EConfig() {}
}
