package com.example.e2e;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Smoke tests — verify all three services are reachable before the full suite runs.
 * Run these first in CI to fail fast if the environment is not ready.
 */
class HealthCheckE2ETest extends BaseE2ETest {

    @Test
    void catalogServiceIsUp() {
        given().baseUri(E2EConfig.CATALOG_BASE_URL)
                .get("/actuator/health")
                .then().statusCode(200);
    }

    @Test
    void orderServiceIsUp() {
        given().baseUri(E2EConfig.ORDER_BASE_URL)
                .get("/actuator/health")
                .then().statusCode(200);
    }

    @Test
    void notificationServiceIsUp() {
        given().baseUri(E2EConfig.NOTIFICATION_BASE_URL)
                .get("/actuator/health")
                .then().statusCode(200);
    }
}
