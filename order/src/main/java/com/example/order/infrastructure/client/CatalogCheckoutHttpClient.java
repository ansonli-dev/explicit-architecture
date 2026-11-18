package com.example.order.infrastructure.client;

import com.example.order.application.checkout.Checkout;
import com.example.order.application.checkout.CheckoutProblem;
import com.example.order.application.port.outbound.CheckoutCatalog;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.example.order.application.checkout.CheckoutProblem.Code.BOOK_NOT_FOUND;
import static com.example.order.application.checkout.CheckoutProblem.Code.CATALOG_DATA_INVALID;
import static com.example.order.application.checkout.CheckoutProblem.Code.CATALOG_UNAVAILABLE;

@Component
@Profile("demo-integrated")
public class CatalogCheckoutHttpClient implements CheckoutCatalog {

    private static final Duration BUDGET = Duration.ofSeconds(4);
    private static final String CHECKOUT_PATH = "/api/v1/internal/checkout";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(BUDGET).build();
    private final ObjectMapper json;
    private final URI catalog;
    private final URI tokenUrl;
    private final String credentials;

    public CatalogCheckoutHttpClient(ObjectMapper json,
            @Value("${demo.catalog.base-url:${DEMO_CATALOG_URL}}") URI catalog,
            @Value("${demo.catalog.token-url:${DEMO_OIDC_TOKEN_URL}}") URI tokenUrl,
            @Value("${demo.catalog.client-secret:${DEMO_ORDER_CLIENT_SECRET}}") String secret) {
        this.json = json;
        this.catalog = httpUri(catalog);
        this.tokenUrl = httpUri(tokenUrl);
        if (secret.isBlank()) {
            throw new IllegalArgumentException("Order service client secret is required");
        }
        this.credentials = "grant_type=client_credentials&client_id=bookstore-order&client_secret="
                + URLEncoder.encode(secret, StandardCharsets.UTF_8);
    }

    @Override
    public List<Checkout.Snapshot> snapshot(List<Checkout.RequestedLine> lines) {
        var response = exchange("POST", CHECKOUT_PATH + "/snapshots", new Lines<>(lines));
        try {
            if (response.statusCode() == 404 || response.statusCode() == 502) {
                String code = json.readTree(response.body()).path("code").asText();
                if (response.statusCode() == 404 && BOOK_NOT_FOUND.name().equals(code)) {
                    throw new CheckoutProblem(BOOK_NOT_FOUND);
                }
                if (response.statusCode() == 502 && CATALOG_DATA_INVALID.name().equals(code)) {
                    throw new CheckoutProblem(CATALOG_DATA_INVALID);
                }
            }
            return List.copyOf(readOk(response, Snapshots.class).lines());
        } catch (JsonProcessingException | NullPointerException e) {
            throw new CheckoutProblem(CATALOG_UNAVAILABLE);
        }
    }

    @Override
    public Checkout.Reservation reserve(UUID submissionId, List<Checkout.Line> lines) {
        var priceLines = lines.stream()
                .map(line -> new PriceLine(line.bookId(), line.quantity(), line.unitPriceMinor(), Checkout.CURRENCY))
                .toList();
        return readOk(exchange("PUT", CHECKOUT_PATH + "/reservations/" + submissionId, new Lines<>(priceLines)),
                Checkout.Reservation.class);
    }

    @Override
    public void release(UUID submissionId) {
        if (exchange("DELETE", CHECKOUT_PATH + "/reservations/" + submissionId, null).statusCode() != 204) {
            throw new CheckoutProblem(CATALOG_UNAVAILABLE);
        }
    }

    private HttpResponse<String> exchange(String method, String path, Object body) {
        long deadline = System.nanoTime() + BUDGET.toNanos();
        var tokenRequest = HttpRequest.newBuilder(tokenUrl).timeout(BUDGET)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(credentials)).build();
        var token = readOk(send(tokenRequest, deadline), Token.class).accessToken();
        if (token == null || token.isBlank()) {
            throw new CheckoutProblem(CATALOG_UNAVAILABLE);
        }
        var catalogRequest = HttpRequest.newBuilder(catalog.resolve(path)).timeout(BUDGET)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        try {
            if (body == null) {
                return send(catalogRequest.DELETE().build(), deadline);
            }
            return send(catalogRequest.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), deadline);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new CheckoutProblem(CATALOG_UNAVAILABLE);
        }
    }

    private HttpResponse<String> send(HttpRequest request, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new CheckoutProblem(CATALOG_UNAVAILABLE);
        }
        var response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            return response.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            response.cancel(true);
            Thread.currentThread().interrupt();
            throw new CheckoutProblem(CATALOG_UNAVAILABLE);
        } catch (ExecutionException | TimeoutException e) {
            response.cancel(true);
            throw new CheckoutProblem(CATALOG_UNAVAILABLE);
        }
    }

    private <T> T readOk(HttpResponse<String> response, Class<T> type) {
        if (response.statusCode() != 200) {
            throw new CheckoutProblem(CATALOG_UNAVAILABLE);
        }
        try {
            T result = json.readValue(response.body(), type);
            if (result == null) {
                throw new CheckoutProblem(CATALOG_UNAVAILABLE);
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new CheckoutProblem(CATALOG_UNAVAILABLE);
        }
    }

    private static URI httpUri(URI uri) {
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Checkout endpoints must be absolute HTTP(S) URLs");
        }
        return uri;
    }

    @PreDestroy
    void close() {
        http.shutdownNow();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Token(@JsonProperty("access_token") String accessToken) {}
    private record Snapshots(List<Checkout.Snapshot> lines) {}
    private record Lines<T>(List<T> lines) {}
    private record PriceLine(UUID bookId, int quantity, long unitPriceMinor, String currency) {}
}
