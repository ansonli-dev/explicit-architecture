package com.example.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@EnabledIfEnvironmentVariable(named = "DEMO_OIDC_BASE_URL", matches = ".+")
class CheckoutE2ETest {
    private static final String CATALOG = E2EConfig.CATALOG_BASE_URL;
    private static final String ORDER = E2EConfig.ORDER_BASE_URL;
    private static final String FIRST_BOOK = "00000000-0000-4000-8000-000000000101";
    private static final String SECOND_BOOK = "00000000-0000-4000-8000-000000000102";
    private static final String LAST_COPY = "00000000-0000-4000-8000-00000000010d";
    private static final String QUOTES = "/api/v1/me/order-quotes";
    private static final String SUBMISSIONS = "/api/v1/me/order-submissions";
    private static final String ORDERS = "/api/v1/me/orders";
    private static final String BOOKS = "/api/v1/books";
    private static final String INTERNAL_RESERVATIONS = "/api/v1/internal/checkout/reservations";
    private static final String CANCEL_TOPIC = "bookstore.order.cancelled";
    private static final String CATALOG_GROUP = "bookstore-demo.catalog";
    private static final Path FIXTURE = Path.of("/state/checkout.json");
    private static final Path CRASH_FIXTURE = Path.of("/state/crash-checkout.json");
    private static final Path RECONCILE_QUOTE = Path.of("/state/reconcile-quote");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private static String readerA;
    private static String readerB;
    private static String admin;
    private static String service;

    @BeforeAll
    static void login() throws Exception {
        var oidc = new DemoOidc();
        readerA = oidc.login("reader-a");
        readerB = oidc.login("reader-b");
        admin = oidc.login("admin");
        service = oidc.serviceToken();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEMO_VERIFY_INTAKE_CLOSED", matches = "false")
    void catalogReadsArePublicAndOnlyTheAdminCanChangeTitles() throws Exception {
        var page1 = get(CATALOG, BOOKS, null, 200);
        var page2 = get(CATALOG, BOOKS + "?page=2", null, 200);
        assertThat(page1.path("books").size()).isEqualTo(12);
        assertThat(get(CATALOG, BOOKS + "/" + FIRST_BOOK, null, 200).path("description").asText())
                .contains("service boundaries");
        assertThat(page1.path("hasNext").asBoolean()).isTrue();
        assertThat(page2.path("books").get(0).path("id").asText()).isEqualTo(LAST_COPY);
        assertThat(page2.path("hasNext").asBoolean()).isFalse();
        assertThat(get(CATALOG, BOOKS + "?page=100", null, 200).path("books")).isEmpty();
        var categories = get(CATALOG, "/api/v1/categories", null, 200).path("categories");
        assertThat(JSON.convertValue(categories, String[].class)).contains("Architecture", "Engineering");

        String adminPath = "/api/v1/admin/books/" + FIRST_BOOK;
        get(CATALOG, adminPath, null, 401);
        get(CATALOG, adminPath, readerA, 403);
        get(ORDER, ORDERS, null, 401);
        get(ORDER, ORDERS, admin, 403);
        get(ORDER, ORDERS, service, 401);
        assertThat(request("POST", ORDER, QUOTES, readerA,
                Map.of("lines", List.of(line(UUID.randomUUID().toString(), 1))), null, 404).path("code").asText())
                .isEqualTo("BOOK_NOT_FOUND");
        request("POST", CATALOG, "/api/v1/internal/checkout/snapshots", readerA,
                Map.of("lines", List.of(line(FIRST_BOOK, 1))), null, 403);
        request("POST", CATALOG, "/api/v1/books", admin, Map.of(), null, 403);
        request("POST", CATALOG, "/api/v1/books/" + FIRST_BOOK + "/stock/release", admin, Map.of(), null, 403);
        request("POST", ORDER, "/api/v1/orders", readerA, Map.of(), null, 403);

        JsonNode original = get(CATALOG, adminPath, admin, 200);
        long version = original.path("version").asLong();
        String changedTitle = "Architecture %_ " + UUID.randomUUID();
        var edit = Map.of("title", changedTitle, "expectedVersion", version);
        request("PATCH", CATALOG, adminPath, readerA, edit, null, 403);
        var edited = request("PATCH", CATALOG, adminPath, admin, edit, null, 200);
        assertThat(edited.path("version").asLong()).isEqualTo(version + 1);
        assertThat(request("PATCH", CATALOG, adminPath, admin, edit, null, 409).path("code").asText())
                .isEqualTo("VERSION_CONFLICT");
        assertThat(get(CATALOG, BOOKS + "/" + FIRST_BOOK, null, 200).path("title").asText()).isEqualTo(changedTitle);
        var matches = get(CATALOG, BOOKS + "?q=%25_", null, 200).path("books");
        assertThat(matches.size()).isEqualTo(1);
        assertThat(matches.get(0).path("id").asText()).isEqualTo(FIRST_BOOK);
        request("PATCH", CATALOG, adminPath, admin,
                Map.of("title", original.path("title").asText(), "expectedVersion", version + 1), null, 200);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEMO_VERIFY_INTAKE_CLOSED", matches = "false")
    void competingSubmissionsPersistOneOrderAndLeaveItReadyForTheIntakeRestart() throws Exception {
        assertThat(stock(LAST_COPY)).isEqualTo(1);
        var discount = request("POST", ORDER, QUOTES, readerA,
                Map.of("lines", List.of(line(FIRST_BOOK, 5), line(SECOND_BOOK, 1))), null, 201);
        assertThat(discount.path("totalMinor").asLong()).isEqualTo(50397);
        String quoteA = quote(readerA, LAST_COPY);
        String quoteB = quote(readerB, LAST_COPY);
        String keyA = UUID.randomUUID().toString();
        String keyB = UUID.randomUUID().toString();
        String idA;
        String idB;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> submit(readerA, quoteA, keyA, 202));
            var duplicate = executor.submit(() -> submit(readerA, quoteA, keyA, 202, 200));
            var competitor = executor.submit(() -> submit(readerB, quoteB, keyB, 202));
            idA = first.get().path("submissionId").asText();
            assertThat(duplicate.get().path("submissionId").asText()).isEqualTo(idA);
            idB = competitor.get().path("submissionId").asText();
            assertThat(idA).isNotEqualTo(idB);
        }
        get(ORDER, SUBMISSIONS + "/" + idA, readerB, 404);
        get(ORDER, SUBMISSIONS + "/" + idB, readerA, 404);
        get(ORDER, QUOTES + "/" + quoteA, readerB, 404);
        get(ORDER, QUOTES + "/" + quoteB, readerA, 404);
        assertThat(get(ORDER, QUOTES + "/" + quoteA, readerA, 200).path("totalMinor").asLong()).isEqualTo(5500);
        assertThat(submit(readerA, discount.path("quoteId").asText(), keyA, 409).path("code").asText())
                .isEqualTo("IDEMPOTENCY_CONFLICT");

        var resultA = terminal(readerA, idA);
        var resultB = terminal(readerB, idB);
        assertThat(List.of(resultA.path("state").asText(), resultB.path("state").asText()))
                .containsExactlyInAnyOrder("PLACED", "REJECTED");
        boolean aWon = resultA.path("state").asText().equals("PLACED");
        var winner = aWon ? resultA : resultB;
        var loser = aWon ? resultB : resultA;
        assertThat(submit(readerA, quoteA, keyA, 200)).isEqualTo(resultA);
        assertThat(submit(readerB, quoteB, keyB, 200)).isEqualTo(resultB);
        assertThat(loser.path("reason").asText()).isEqualTo("INSUFFICIENT_STOCK");
        String id = winner.path("submissionId").asText();
        assertThat(winner.path("order").path("orderId").asText()).isEqualTo(id);
        assertThat(stock(LAST_COPY)).isZero();
        assertThat(request("POST", ORDER, QUOTES, readerA,
                Map.of("lines", List.of(line(LAST_COPY, 1))), null, 409).path("code").asText())
                .isEqualTo("INSUFFICIENT_STOCK");

        String owner = aWon ? readerA : readerB;
        String other = aWon ? readerB : readerA;
        var placed = get(ORDER, ORDERS + "/" + id, owner, 200);
        assertThat(placed.path("status").asText()).isEqualTo("PLACED");
        String createdAt = placed.path("createdAt").asText();
        assertThat(Instant.parse(createdAt)).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(1));
        assertThat(winner.at("/order/createdAt").asText()).isEqualTo(createdAt);
        var expanded = get(ORDER, ORDERS + "?include=lines", owner, 200).path("orders").get(0);
        assertThat(expanded.path("orderId").asText()).isEqualTo(id);
        assertThat(expanded.path("createdAt").asText()).isEqualTo(createdAt);
        assertThat(expanded.path("lines")).isEqualTo(placed.path("lines"));
        assertThat(expanded.at("/lines/0/title").asText()).isEqualTo("The Last Copy");
        assertThat(expanded.at("/lines/0/quantity").asInt()).isEqualTo(1);
        assertThat(expanded.at("/lines/0/unitPriceMinor").asInt()).isEqualTo(5500);
        assertThat(expanded.path("totalMinor").asInt()).isEqualTo(5500);
        assertThat(expanded.path("currency").asText()).isEqualTo("CNY");
        assertThat(placed.has("email") || placed.has("customerEmail")).isFalse();
        assertThat(get(ORDER, ORDERS, owner, 200).path("orders").findValuesAsText("orderId")).contains(id);
        assertThat(get(ORDER, ORDERS, other, 200).path("orders").findValuesAsText("orderId")).doesNotContain(id);
        get(ORDER, ORDERS + "/" + id, other, 404);
        JSON.writeValue(FIXTURE.toFile(), Map.of(
                "owner", aWon ? "reader-a" : "reader-b",
                "quoteId", aWon ? quoteA : quoteB,
                "key", aWon ? keyA : keyB,
                "orderId", id,
                "pendingQuoteId", quote(owner, FIRST_BOOK)));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEMO_VERIFY_INTAKE_CLOSED", matches = "false")
    @EnabledIfEnvironmentVariable(named = "DEMO_CATALOG_JDBC_URL", matches = ".+")
    @EnabledIfEnvironmentVariable(named = "DEMO_ORDER_JDBC_URL", matches = ".+")
    void checkoutRejectsChangedPricesAndExpiredQuotesButKeepsPurchasedSnapshots() throws Exception {
        var original = get(CATALOG, BOOKS + "/" + FIRST_BOOK, null, 200);
        var title = original.path("title").asText();
        long price = original.path("priceMinor").asLong();
        int available = original.path("stock").asInt();
        var bought = submit(readerA, quote(readerA, FIRST_BOOK), UUID.randomUUID().toString(), 202)
                .path("submissionId").asText();
        assertThat(terminal(readerA, bought).path("state").asText()).isEqualTo("PLACED");
        String orderPath = ORDERS + "/" + bought;
        String oldPriceQuote = quote(readerA, FIRST_BOOK);
        try {
            update("DEMO_CATALOG_JDBC_URL", "UPDATE book SET title=?, price_cents=? WHERE id=?",
                    "Updated for price verification", price + 100, UUID.fromString(FIRST_BOOK));
            var result = submit(readerA, oldPriceQuote, UUID.randomUUID().toString(), 202).path("submissionId").asText();
            var rejected = terminal(readerA, result);
            assertThat(rejected.path("state").asText()).isEqualTo("REJECTED");
            assertThat(rejected.path("reason").asText()).isEqualTo("PRICE_CHANGED");
            assertThat(stock(FIRST_BOOK)).isEqualTo(available - 1);
            var order = get(ORDER, orderPath, readerA, 200);
            assertThat(order.at("/lines/0/title").asText()).isEqualTo(title);
            assertThat(order.at("/lines/0/unitPriceMinor").asLong()).isEqualTo(price);
            var orders = get(ORDER, ORDERS + "?include=lines", readerA, 200).path("orders");
            assertThat(orders).anySatisfy(row -> {
                assertThat(row.path("orderId").asText()).isEqualTo(bought);
                assertThat(row.path("lines")).isEqualTo(order.path("lines"));
                assertThat(row.path("totalMinor").asLong()).isEqualTo(price);
            });
        } finally {
            update("DEMO_CATALOG_JDBC_URL", "UPDATE book SET title=?, price_cents=? WHERE id=?",
                    title, price, UUID.fromString(FIRST_BOOK));
            long version = get(ORDER, orderPath, readerA, 200).path("version").asLong();
            request("PUT", ORDER, orderPath + "/cancellation", readerA, cancelBody(version), null, 201);
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(stock(FIRST_BOOK)).isEqualTo(available));
        }

        var expired = quote(readerA, FIRST_BOOK);
        update("DEMO_ORDER_JDBC_URL", "UPDATE checkout_quote SET expires_at=now() - interval '1 second' WHERE id=?",
                UUID.fromString(expired));
        assertThat(submit(readerA, expired, UUID.randomUUID().toString(), 409).path("code").asText())
                .isEqualTo("QUOTE_EXPIRED");
    }

    private static void update(String urlVariable, String statement, Object... params) throws Exception {
        try (var connection = DriverManager.getConnection(System.getenv(urlVariable), "bookstore", "bookstore");
             var update = connection.prepareStatement(statement)) {
            for (int i = 0; i < params.length; i++) {
                update.setObject(i + 1, params[i]);
            }
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEMO_VERIFY_INTAKE_CLOSED", matches = "true")
    void closedIntakeStillReplaysAndCancelsAndTheKafkaReplayCannotReleaseAnotherHold() throws Exception {
        assertThat(FIXTURE).exists();
        var fixture = JSON.readTree(FIXTURE.toFile());
        boolean aWon = fixture.path("owner").asText().equals("reader-a");
        String owner = aWon ? readerA : readerB;
        String other = aWon ? readerB : readerA;
        String orderId = fixture.path("orderId").asText();
        var replay = submit(owner, fixture.path("quoteId").asText(), fixture.path("key").asText(), 200);
        assertThat(replay.path("submissionId").asText()).isEqualTo(orderId);
        assertThat(replay.path("state").asText()).isEqualTo("PLACED");
        assertThat(submit(owner, fixture.path("pendingQuoteId").asText(), UUID.randomUUID().toString(), 503)
                .path("code").asText()).isEqualTo("INTAKE_CLOSED");
        assertThat(request("POST", ORDER, QUOTES, owner, Map.of("lines", List.of(line(FIRST_BOOK, 1))),
                null, 503).path("code").asText()).isEqualTo("INTAKE_CLOSED");

        String orderPath = ORDERS + "/" + orderId;
        String cancellationPath = orderPath + "/cancellation";
        long version = get(ORDER, orderPath, owner, 200).path("version").asLong();
        request("PUT", ORDER, cancellationPath, other, cancelBody(version), null, 404);
        assertThat(request("PUT", ORDER, cancellationPath, owner, cancelBody(version + 1), null, 409)
                .path("code").asText()).isEqualTo("VERSION_CONFLICT");
        var cancellation = request("PUT", ORDER, cancellationPath, owner, cancelBody(version), null, 201);
        assertThat(cancellation.path("orderId").asText()).isEqualTo(orderId);
        assertThat(cancellation.path("reason").asText()).isEqualTo("Demo cancellation");
        assertThat(Instant.parse(cancellation.path("cancelledAt").asText())).isBeforeOrEqualTo(Instant.now());
        assertThat(request("PUT", ORDER, cancellationPath, owner, cancelBody(version), null, 200)).isEqualTo(cancellation);
        assertThat(get(ORDER, cancellationPath, owner, 200)).isEqualTo(cancellation);
        get(ORDER, cancellationPath, other, 404);
        request("PUT", ORDER, orderPath + "/cancel", owner, cancelBody(version), null, 403);
        var cancelled = get(ORDER, orderPath, owner, 200);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("version").asLong()).isEqualTo(version + 1);
        assertThat(request("PUT", ORDER, cancellationPath, owner, cancelBody(version + 1), null, 409)
                .path("code").asText()).isEqualTo("ORDER_STATE_CONFLICT");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(stock(LAST_COPY)).isEqualTo(1));

        String hold = INTERNAL_RESERVATIONS + "/" + UUID.randomUUID();
        var held = request("PUT", CATALOG, hold, service,
                Map.of("lines", List.of(Map.of("bookId", LAST_COPY, "quantity", 1,
                        "unitPriceMinor", 5500, "currency", "CNY"))), null, 200);
        assertThat(held.path("state").asText()).isEqualTo("HELD");
        try {
            replayCancellation(orderId);
            assertThat(stock(LAST_COPY)).isZero();
        } finally {
            request("DELETE", CATALOG, hold, service, null, null, 204);
        }
        assertThat(stock(LAST_COPY)).isEqualTo(1);
        Files.delete(FIXTURE);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEMO_VERIFY_RECONCILE", matches = "prepare")
    void prepareReconciliation() throws Exception {
        Files.writeString(RECONCILE_QUOTE, quote(readerA, FIRST_BOOK));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEMO_VERIFY_RECONCILE", matches = "verify")
    void aCatalogOutageLeavesSubmissionVisibleForReconciliation() throws Exception {
        var quoteId = Files.readString(RECONCILE_QUOTE);
        var key = UUID.randomUUID().toString();
        var id = submit(readerA, quoteId, key, 202).path("submissionId").asText();
        var result = await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1))
                .until(() -> get(ORDER, SUBMISSIONS + "/" + id, readerA, 200),
                        submission -> submission.path("state").asText().equals("RECONCILE_REQUIRED"));
        assertThat(result.has("order")).isFalse();
        assertThat(submit(readerA, quoteId, key, 202)).isEqualTo(result);
        get(ORDER, SUBMISSIONS + "/" + id, readerB, 404);
        get(ORDER, ORDERS + "/" + id, readerA, 404);
        Files.delete(RECONCILE_QUOTE);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEMO_VERIFY_CRASH_RECOVERY", matches = "true")
    void prepareCrashRecovery() throws Exception {
        int available = stock(LAST_COPY);
        assertThat(available).isEqualTo(1);
        String quoteId = quote(readerA, LAST_COPY);
        String key = UUID.randomUUID().toString();
        String id = submit(readerA, quoteId, key, 202).path("submissionId").asText();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(stock(LAST_COPY)).isZero());
        assertThat(get(ORDER, SUBMISSIONS + "/" + id, readerA, 200).path("state").asText()).isEqualTo("PROCESSING");
        get(ORDER, ORDERS + "/" + id, readerA, 404);
        JSON.writeValue(CRASH_FIXTURE.toFile(), Map.of(
                "quoteId", quoteId, "key", key, "orderId", id, "stockBefore", available));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "DEMO_VERIFY_CRASH_RECOVERY", matches = "true")
    void verifyCrashRecovery() throws Exception {
        assertThat(CRASH_FIXTURE).exists();
        var fixture = JSON.readTree(CRASH_FIXTURE.toFile());
        String id = fixture.path("orderId").asText();
        int stockBefore = fixture.path("stockBefore").asInt();
        var completed = terminal(readerA, id);
        assertThat(completed.path("state").asText()).isEqualTo("PLACED");
        assertThat(completed.path("order").path("orderId").asText()).isEqualTo(id);
        var replay = submit(readerA, fixture.path("quoteId").asText(), fixture.path("key").asText(), 200);
        assertThat(replay.path("submissionId").asText()).isEqualTo(id);
        assertThat(replay.path("state").asText()).isEqualTo("PLACED");
        assertThat(stock(LAST_COPY)).isEqualTo(stockBefore - 1);
        String path = ORDERS + "/" + id;
        var order = get(ORDER, path, readerA, 200);
        assertThat(order.path("status").asText()).isEqualTo("PLACED");
        request("PUT", ORDER, path + "/cancellation", readerA, cancelBody(order.path("version").asLong()), null, 201);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(stock(LAST_COPY)).isEqualTo(stockBefore));
        Files.delete(CRASH_FIXTURE);
    }

    private static JsonNode terminal(String token, String id) {
        return await().atMost(Duration.ofSeconds(30))
                .until(() -> get(ORDER, SUBMISSIONS + "/" + id, token, 200),
                        submission -> !submission.path("state").asText().equals("PROCESSING"));
    }

    private static String quote(String token, String bookId) throws Exception {
        return request("POST", ORDER, QUOTES, token, Map.of("lines", List.of(line(bookId, 1))), null, 201)
                .path("quoteId").asText();
    }

    private static JsonNode submit(String token, String quoteId, String key, int... statuses) throws Exception {
        return request("POST", ORDER, SUBMISSIONS, token, Map.of("quoteId", quoteId), key, statuses);
    }

    private static int stock(String id) throws Exception {
        return get(CATALOG, BOOKS + "/" + id, null, 200).path("stock").asInt(-1);
    }

    private static Map<String, Object> line(String bookId, int quantity) {
        return Map.of("bookId", bookId, "quantity", quantity);
    }

    private static Map<String, Object> cancelBody(long version) {
        return Map.of("reason", "Demo cancellation", "expectedVersion", version);
    }

    private static JsonNode get(String base, String path, String token, int status) throws Exception {
        return request("GET", base, path, token, null, null, status);
    }

    private static JsonNode request(String method, String base, String path, String token, Object body,
                                    String key, int... statuses) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        var payload = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            request.header("Content-Type", "application/json");
            payload = HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
        }
        var response = HTTP.send(request.method(method, payload).build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
        assertThat(response.statusCode()).as("%s %s (code: %s)", method, path, json.path("code").asText())
                .isIn(IntStream.of(statuses).boxed().toList());
        if (response.statusCode() < 300) {
            if (method.equals("POST") && path.equals(QUOTES)) {
                assertThat(response.headers().firstValue("Location")).contains(path + "/" + json.path("quoteId").asText());
            } else if (method.equals("POST") && path.equals(SUBMISSIONS)) {
                assertThat(response.headers().firstValue("Location")).contains(path + "/" + json.path("submissionId").asText());
            } else if (method.equals("PUT") && path.endsWith("/cancellation")) {
                assertThat(response.headers().firstValue("Location")).contains(path);
            }
        }
        return json;
    }

    private static void replayCancellation(String orderId) throws Exception {
        var schema = get(System.getenv("DEMO_SCHEMA_REGISTRY_URL"), "/subjects/" + CANCEL_TOPIC
                + "-value/versions/latest", null, 200);
        int schemaId = schema.path("id").asInt();
        assertThat(schemaId).isPositive();
        assertThat(JSON.readTree(schema.path("schema").asText()).path("fields").findValuesAsText("name"))
                .contains("reservationProtocol");
        Map<String, Object> settings = Map.of(
                "bootstrap.servers", System.getenv("DEMO_KAFKA_BOOTSTRAP"),
                "request.timeout.ms", 4000,
                "default.api.timeout.ms", 6000);
        try (var consumer = new KafkaConsumer<>(settings, new StringDeserializer(), new ByteArrayDeserializer());
             var producer = new KafkaProducer<>(settings, new StringSerializer(), new ByteArraySerializer());
             var kafka = AdminClient.create(settings)) {
            var partitions = consumer.partitionsFor(CANCEL_TOPIC).stream()
                    .map(partition -> new TopicPartition(CANCEL_TOPIC, partition.partition())).toList();
            consumer.assign(partitions);
            consumer.seekToBeginning(partitions);
            var deadline = Instant.now().plusSeconds(15);
            while (Instant.now().isBefore(deadline)) {
                for (var original : consumer.poll(Duration.ofMillis(500))) {
                    if (orderId.equals(original.key())) {
                        var wire = ByteBuffer.wrap(original.value());
                        assertThat(wire.get()).isZero();
                        assertThat(wire.getInt()).isEqualTo(schemaId);
                        var duplicate = new ProducerRecord<>(CANCEL_TOPIC, original.partition(), original.key(),
                                original.value(), original.headers());
                        var record = producer.send(duplicate).get(10, TimeUnit.SECONDS);
                        var partition = new TopicPartition(CANCEL_TOPIC, record.partition());
                        await().atMost(Duration.ofSeconds(30)).until(() -> {
                            var committed = kafka.listConsumerGroupOffsets(CATALOG_GROUP)
                                    .partitionsToOffsetAndMetadata().get(6, TimeUnit.SECONDS).get(partition);
                            return committed != null && committed.offset() > record.offset();
                        });
                        return;
                    }
                }
            }
            throw new AssertionError("Order cancellation was not published to " + CANCEL_TOPIC);
        }
    }
}
