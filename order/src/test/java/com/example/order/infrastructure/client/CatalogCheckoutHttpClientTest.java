package com.example.order.infrastructure.client;

import com.example.order.application.checkout.Checkout;
import com.example.order.application.checkout.CheckoutProblem;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogCheckoutHttpClientTest {

    @Test
    void invalidCatalogDataRetainsItsErrorCode() throws Exception {
        try (var tokenServer = new MockWebServer(); var catalog = new MockWebServer()) {
            tokenServer.enqueue(new MockResponse().setBody("{\"access_token\":\"order-service-token\"}"));
            catalog.enqueue(new MockResponse().setResponseCode(502).setBody("{\"code\":\"CATALOG_DATA_INVALID\"}"));
            var client = new CatalogCheckoutHttpClient(new ObjectMapper(), catalog.url("/").uri(),
                    tokenServer.url("/token").uri(), "fixture-secret");
            try {
                assertThatThrownBy(() -> client.snapshot(List.of(new Checkout.RequestedLine(UUID.randomUUID(), 1))))
                        .isInstanceOfSatisfying(CheckoutProblem.class,
                                p -> assertThat(p.code()).isEqualTo(CheckoutProblem.Code.CATALOG_DATA_INVALID));
            } finally {
                client.close();
            }
        }
    }

    @Test
    void serviceTokenAndResponseBodyShareOneDeadline() throws Exception {
        try (var tokenServer = new MockWebServer(); var catalog = new MockWebServer()) {
            tokenServer.enqueue(new MockResponse().setBody("{\"access_token\":\"order-service-token\"}")
                    .setBodyDelay(2, TimeUnit.SECONDS));
            catalog.enqueue(new MockResponse().setBody("{\"state\":\"HELD\"}").setBodyDelay(3, TimeUnit.SECONDS));
            var client = new CatalogCheckoutHttpClient(new ObjectMapper(), catalog.url("/").uri(),
                    tokenServer.url("/token").uri(), "fixture-secret");
            try {
                assertThatThrownBy(() -> client.reserve(UUID.randomUUID(), List.of(
                        new Checkout.Line(UUID.randomUUID(), "Fixture", 1, 4900))))
                        .isInstanceOfSatisfying(CheckoutProblem.class,
                                p -> assertThat(p.code()).isEqualTo(CheckoutProblem.Code.CATALOG_UNAVAILABLE));
                var request = catalog.takeRequest(1, TimeUnit.SECONDS);
                assertThat(request).isNotNull();
                assertThat(request.getHeader("Authorization")).isEqualTo("Bearer order-service-token");
            } finally {
                client.close();
            }
        }
    }
}
