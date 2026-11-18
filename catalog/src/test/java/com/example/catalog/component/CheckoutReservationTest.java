package com.example.catalog.component;

import com.example.catalog.interfaces.messaging.consumer.OrderEventConsumer;
import com.example.catalog.support.DemoTokens;
import com.example.events.KafkaResourceConstants;
import com.example.events.v1.OrderCancelled;
import com.example.events.v1.OrderItem;
import org.apache.avro.Schema;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.consumer.group-id=catalog-checkout-component", "outbox.relay.strategy=disabled"
})
@ActiveProfiles({"test", "demo-integrated"})
@AutoConfigureMockMvc
@EmbeddedKafka(topics = KafkaResourceConstants.TOPIC_ORDER_CANCELLED,
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class CheckoutReservationTest {
    private static final DemoTokens TOKENS = new DemoTokens();
    private static final String INTERNAL = "/api/v1/internal/checkout";
    private static final String GROUP = "catalog-checkout-component";
    private static final String TOPIC = KafkaResourceConstants.TOPIC_ORDER_CANCELLED;

    @DynamicPropertySource
    static void security(DynamicPropertyRegistry registry) {
        TOKENS.properties(registry);
    }

    @AfterAll
    static void closeJwks() {
        TOKENS.close();
    }

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired EmbeddedKafkaBroker kafka;
    @Autowired KafkaTemplate<String, Object> publisher;
    @Autowired OrderEventConsumer consumer;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @BeforeEach
    void fixtures() {
        jdbc.execute("DELETE FROM checkout_reservation_line");
        jdbc.execute("DELETE FROM checkout_reservation");
        jdbc.execute("DELETE FROM book");
        jdbc.execute("DELETE FROM category");
        jdbc.execute("INSERT INTO category VALUES ('00000000-0000-4000-8000-000000000001', 'Architecture')");
        for (int i = 1; i <= 13; i++) {
            jdbc.update("""
                    INSERT INTO book (id, title, author_name, price_cents, currency, category_id, stock_total, stock_reserved)
                    VALUES (CAST(? AS UUID), ?, ?, 4900, 'CNY', '00000000-0000-4000-8000-000000000001', 1, 0)
                    """, book(i), i == 1 ? "Literal %_\\ Token" : "Same title", "Author");
        }
    }

    @Test
    void includesOptionalBookPresentationInThePublicContract() throws Exception {
        jdbc.update("UPDATE book SET cover_url=?, description=? WHERE id=CAST(? AS uuid)",
                "https://example.test/book.png", "A book about transactions.", book(1));
        mvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].coverUrl").value("https://example.test/book.png"))
                .andExpect(jsonPath("$.books[0].description").doesNotExist())
                .andExpect(jsonPath("$.books[1].coverUrl").doesNotExist());
        mvc.perform(get("/api/v1/books/{id}", book(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").value("https://example.test/book.png"))
                .andExpect(jsonPath("$.description").value("A book about transactions."));
        mvc.perform(get("/api/v1/books/{id}", book(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void publicPagesUseDeterministicOffsetsAndLiteralCaseInsensitiveSearch() throws Exception {
        mvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.books.length()").value(12))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.books[0].id").value(book(1)))
                .andExpect(jsonPath("$.books[0].priceMinor").value(4900));
        mvc.perform(get("/api/v1/books").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.books.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.books[0].id").value(book(13)));
        mvc.perform(get("/api/v1/books").param("page", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.books.length()").value(0));
        mvc.perform(get("/api/v1/books").param("q", "lItErAl %_\\").param("category", "Architecture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.books[0].id").value(book(1)));
        mvc.perform(get("/api/v1/books").param("q", "%_\\").param("category", "architecture"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.books.length()").value(0));
        mvc.perform(get("/api/v1/books/{id}", book(99)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categories[0]").value("Architecture"));
    }

    @Test
    void snapshotsAndAdminReadAuthoritativeDataAndStrictlyValidateInput() throws Exception {
        mvc.perform(service(json(post(INTERNAL + "/snapshots"), lines(line(1, 2, 0, false)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].unitPriceMinor").value(4900))
                .andExpect(jsonPath("$.lines[0].quantity").value(2))
                .andExpect(jsonPath("$.lines[0].stock").value(1));
        mvc.perform(service(json(post(INTERNAL + "/snapshots"), lines(line(99, 1, 0, false)))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
        mvc.perform(service(json(post(INTERNAL + "/snapshots"), lines(line(1, 1, 4900, true)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(service(json(post(INTERNAL + "/snapshots"), lines(line(1, 1, 0, false), line(1, 1, 0, false)))))
                .andExpect(status().isBadRequest());
        mvc.perform(admin(get("/api/v1/admin/books/{bookId}", book(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mvc.perform(admin(json(patch("/api/v1/admin/books/{bookId}", book(1)),
                "{\"title\":\"wrong\",\"expectedVersion\":0,\"stock\":9}"))).andExpect(status().isBadRequest());
        mvc.perform(rename(1, "First Edit", 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.stock").value(1));
        mvc.perform(rename(1, "Stale Edit", 0)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        mvc.perform(get("/api/v1/books/{id}", book(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("First Edit"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void unsafeStoredAmountIsRejectedAndDoesNotAllowAnAdminWrite() throws Exception {
        jdbc.update("UPDATE book SET price_cents = 9007199254740992 WHERE id = CAST(? AS UUID)", book(1));
        mvc.perform(get("/api/v1/books/{id}", book(1))).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CATALOG_DATA_INVALID"));
        mvc.perform(service(json(post(INTERNAL + "/snapshots"), lines(line(1, 1, 0, false)))))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("CATALOG_DATA_INVALID"));
        mvc.perform(rename(1, "Should Roll Back", 0)).andExpect(status().isBadGateway());
        assertThat(jdbc.queryForObject("SELECT version FROM book WHERE id = CAST(? AS UUID)",
                Long.class, book(1))).isZero();
    }

    @Test
    void migrationPreservesExistingBooksAndLegacyAvroRetries() throws Exception {
        Flyway.configure().dataSource(dataSource).defaultSchema("catalog_upgrade_test")
                .locations("classpath:db/seedwork", "classpath:db/migration").target("0101").load().migrate();
        jdbc.execute("INSERT INTO catalog_upgrade_test.category VALUES ('00000000-0000-4000-8000-000000000001', 'Architecture')");
        jdbc.update("""
                INSERT INTO catalog_upgrade_test.book
                    (id, title, author_name, price_cents, currency, category_id, stock_total, stock_reserved, version)
                VALUES (CAST(? AS UUID), 'Legacy Title', 'Author', 4900, 'CNY',
                    '00000000-0000-4000-8000-000000000001', 5, 2, 7)
                """, book(1));
        var cancellation = cancel(UUID.randomUUID(), 1);
        var legacyBytes = legacyCancellationBytes(cancellation);
        var reader = new SpecificDatumReader<>(OrderCancelled.class);
        assertThatThrownBy(() -> reader.read(null, DecoderFactory.get().binaryDecoder(legacyBytes, null)))
                .isInstanceOf(EOFException.class);
        jdbc.update("""
                INSERT INTO catalog_upgrade_test.consumer_retry_events
                    (id, event_id, topic, avro_class_name, avro_payload, consumer_group, last_failed_at, next_retry_at)
                VALUES (?, ?, ?, ?, ?, 'catalog.legacy', now(), now())
                """, UUID.randomUUID(), UUID.fromString(cancellation.getEventId()), TOPIC,
                OrderCancelled.class.getName(), legacyBytes);
        Flyway.configure().dataSource(dataSource).defaultSchema("catalog_upgrade_test")
                .locations("classpath:db/seedwork", "classpath:db/migration").load().migrate();
        var preserved = jdbc.queryForMap("""
                SELECT title, stock_reserved, version FROM catalog_upgrade_test.book WHERE id = CAST(? AS UUID)
                """, book(1));
        assertThat(preserved).containsEntry("title", "Legacy Title").containsEntry("stock_reserved", 2)
                .containsEntry("version", 7L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM catalog_upgrade_test.checkout_reservation",
                Long.class)).isZero();
        byte[] migrated = jdbc.queryForObject("SELECT avro_payload FROM catalog_upgrade_test.consumer_retry_events", byte[].class);
        assertThat(new SpecificDatumReader<>(OrderCancelled.class)
                .read(null, DecoderFactory.get().binaryDecoder(migrated, null))).isEqualTo(cancellation);
    }

    private static byte[] legacyCancellationBytes(OrderCancelled event) throws Exception {
        var current = OrderCancelled.getClassSchema();
        var legacy = Schema.createRecord(current.getName(), current.getDoc(), current.getNamespace(), false);
        legacy.setFields(current.getFields().stream().filter(f -> !f.name().equals("reservationProtocol"))
                .map(f -> new Schema.Field(f, f.schema())).toList());
        var bytes = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(bytes, null);
        new SpecificDatumWriter<OrderCancelled>(legacy).write(event, encoder);
        encoder.flush();
        return bytes.toByteArray();
    }

    @Test
    void lastCopyHasOneWinnerAndMultiLineRejectionDoesNotChangeOtherBooks() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        var outcomes = race(() -> reserveState(a, line(2, 1)), () -> reserveState(b, line(2, 1)));
        assertThat(outcomes).containsExactlyInAnyOrder("HELD", "REJECTED");
        assertThat(reserved(2)).isEqualTo(1);

        UUID rejected = UUID.randomUUID();
        mvc.perform(reserve(rejected, line(1, 1), line(2, 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_STOCK"));
        assertThat(reserved(1)).isZero();
        mvc.perform(service(delete(INTERNAL + "/reservations/{submissionId}", a))).andExpect(status().isNoContent());
        mvc.perform(service(delete(INTERNAL + "/reservations/{submissionId}", b))).andExpect(status().isNoContent());
        mvc.perform(reserve(rejected, line(1, 1), line(2, 1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_STOCK"));
        assertThat(reserved(1)).isZero();
        assertThat(reserved(2)).isZero();

        mvc.perform(reserve(UUID.randomUUID(), line(1, 1), line(2, 1, 4901, true))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("PRICE_CHANGED"));
        assertThat(reserved(1)).isZero();
    }

    @Test
    void sameIdCanonicalReplayConflictingPayloadAndReleaseTombstones() throws Exception {
        UUID id = UUID.randomUUID();
        var results = race(() -> reserveState(id, line(1, 1), line(2, 1)),
                () -> reserveState(id, line(2, 1), line(1, 1)));
        assertThat(results).containsExactly("HELD", "HELD");
        assertThat(reserved(1)).isEqualTo(1);
        assertThat(reserved(2)).isEqualTo(1);
        mvc.perform(reserve(id, line(1, 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_CONFLICT"));
        mvc.perform(service(delete(INTERNAL + "/reservations/{submissionId}", id))).andExpect(status().isNoContent());
        mvc.perform(service(delete(INTERNAL + "/reservations/{submissionId}", id))).andExpect(status().isNoContent());
        assertThat(reserveState(id, line(1, 1), line(2, 1))).isEqualTo("RELEASED");

        UUID releasedFirst = UUID.randomUUID();
        mvc.perform(service(delete(INTERNAL + "/reservations/{submissionId}", releasedFirst)))
                .andExpect(status().isNoContent());
        assertThat(reserveState(releasedFirst, line(1, 1))).isEqualTo("RELEASED");

        UUID concurrent = UUID.randomUUID();
        race(() -> reserveState(concurrent, line(1, 1)), () -> {
            mvc.perform(service(delete(INTERNAL + "/reservations/{submissionId}", concurrent)))
                    .andExpect(status().isNoContent());
            return "deleted";
        });
        assertThat(reserveState(concurrent, line(1, 1))).isEqualTo("RELEASED");
        assertThat(reserved(1)).isZero();
        assertThat(reserved(2)).isZero();
    }

    @Test
    void titleWritersConflictOnVersionButTitleAndStockEditsBothSurvive() throws Exception {
        var updates = race(() -> mvc.perform(rename(1, "First", 0)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(rename(1, "Second", 0)).andReturn().getResponse().getStatus());
        assertThat(updates).containsExactlyInAnyOrder(200, 409);

        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var renamed = new CountDownLatch(1);
            var commit = new CountDownLatch(1);
            var rename = threads.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                try {
                    mvc.perform(rename(1, "Final", 1)).andExpect(status().isOk());
                    renamed.countDown();
                    if (!commit.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to commit admin write");
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
            try {
                assertThat(renamed.await(10, TimeUnit.SECONDS)).isTrue();
                var requestStarted = new CountDownLatch(1);
                var reserve = threads.submit(() -> {
                    requestStarted.countDown();
                    return reserveState(UUID.randomUUID(), line(1, 1));
                });
                assertThat(requestStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> reserve.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                commit.countDown();
                assertThat(reserve.get(10, TimeUnit.SECONDS)).isEqualTo("HELD");
                rename.get(10, TimeUnit.SECONDS);
            } finally {
                commit.countDown();
            }
        }
        mvc.perform(admin(get("/api/v1/admin/books/{id}", book(1)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final")).andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void cancellationAcknowledgesOnlyOnceReleaseIsVisibleAndNeverFallsBack() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("UPDATE book SET stock_total = 2, stock_reserved = 1 WHERE id = CAST(? AS UUID)", book(1));
        assertThat(reserveState(id, line(1, 1))).isEqualTo("HELD");

        var unsupported = cancel(id, 99);
        Acknowledgment rejected = mock(Acknowledgment.class);
        assertThatThrownBy(() -> consumer.onOrderCancelled(record(unsupported), rejected))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(rejected);
        assertThat(reserved(1)).isEqualTo(2);

        var valid = cancel(id, 2);
        Acknowledgment acknowledge = mock(Acknowledgment.class);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            doAnswer(invocation -> {
                assertThat(executor.submit(() -> state(id)).get(10, TimeUnit.SECONDS)).isEqualTo("RELEASED");
                return null;
            }).when(acknowledge).acknowledge();
            consumer.onOrderCancelled(record(valid), acknowledge);
            consumer.onOrderCancelled(record(valid), acknowledge);
            consumer.onOrderCancelled(record(cancel(id, 2)), acknowledge);
        }
        verify(acknowledge, times(3)).acknowledge();
        // The event deliberately lists a different book; v2 restores only the persisted reservation.
        assertThat(reserved(1)).isEqualTo(1);
        assertThat(reserved(2)).isZero();
    }

    @Test
    void brokerRedeliversAReleaseAfterDatabaseFailureWithoutAdvancingItsOffset() throws Exception {
        UUID id = UUID.randomUUID();
        assertThat(reserveState(id, line(1, 1))).isEqualTo("HELD");

        jdbc.execute("CREATE SEQUENCE checkout_release_attempts");
        jdbc.execute("""
                CREATE FUNCTION fail_checkout_release() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    PERFORM nextval('checkout_release_attempts');
                    RAISE EXCEPTION 'Injected catalog cancellation write failure';
                END; $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_checkout_release BEFORE UPDATE ON checkout_reservation
                FOR EACH ROW WHEN (OLD.state = 'HELD' AND NEW.state = 'RELEASED')
                EXECUTE FUNCTION fail_checkout_release()
                """);
        var event = cancel(id, 2);
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", kafka.getBrokersAsString()))) {
            var metadata = publisher.send(TOPIC, id.toString(), event).get(10, TimeUnit.SECONDS).getRecordMetadata();
            var partition = new TopicPartition(TOPIC, metadata.partition());
            try {
                await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "SELECT last_value FROM checkout_release_attempts", Long.class)).isGreaterThanOrEqualTo(11L));
                assertThat(state(id)).isEqualTo("HELD");
                assertThat(reserved(1)).isEqualTo(1);
                var committed = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                        .get(10, TimeUnit.SECONDS).get(partition);
                assertThat(committed == null || committed.offset() <= metadata.offset()).isTrue();
            } finally {
                jdbc.execute("DROP TRIGGER fail_checkout_release ON checkout_reservation");
                jdbc.execute("DROP FUNCTION fail_checkout_release()");
                jdbc.execute("DROP SEQUENCE checkout_release_attempts");
            }
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                assertThat(state(id)).isEqualTo("RELEASED");
                var offset = admin.listConsumerGroupOffsets(GROUP).partitionsToOffsetAndMetadata()
                        .get(10, TimeUnit.SECONDS).get(partition);
                assertThat(offset).isNotNull();
                assertThat(offset.offset()).isEqualTo(metadata.offset() + 1);
            });
            assertThat(reserved(1)).isZero();
        }
    }

    private static OrderCancelled cancel(UUID id, int protocol) {
        return OrderCancelled.newBuilder().setEventId(UUID.randomUUID().toString()).setOrderId(id.toString())
                .setCustomerId(UUID.randomUUID().toString()).setReason("component test")
                .setOccurredAt(Instant.now()).setReservationProtocol(protocol)
                .setItems(List.of(OrderItem.newBuilder().setBookId(book(2)).setBookTitle("Untrusted")
                        .setQuantity(1).setUnitPriceCents(4900).build())).build();
    }

    private static ConsumerRecord<String, OrderCancelled> record(OrderCancelled event) {
        return new ConsumerRecord<>(TOPIC, 0, 0, event.getOrderId().toString(), event);
    }

    private int reserved(int book) {
        return jdbc.queryForObject("SELECT stock_reserved FROM book WHERE id = CAST(? AS UUID)", Integer.class, book(book));
    }

    private String state(UUID id) {
        return jdbc.queryForObject("SELECT state FROM checkout_reservation WHERE submission_id = ?", String.class, id);
    }

    private String reserveState(UUID id, String... bodyLines) throws Exception {
        var response = mvc.perform(reserve(id, bodyLines)).andExpect(status().isOk()).andReturn().getResponse();
        return com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.state");
    }

    private static MockHttpServletRequestBuilder reserve(UUID id, String... bodyLines) {
        return service(json(put(INTERNAL + "/reservations/{submissionId}", id), lines(bodyLines)));
    }

    private static MockHttpServletRequestBuilder rename(int book, String title, long version) {
        return admin(json(patch("/api/v1/admin/books/{bookId}", book(book)), """
                {"title":"%s","expectedVersion":%d}
                """.formatted(title, version)));
    }

    private static MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TOKENS.admin());
    }

    private static MockHttpServletRequestBuilder service(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + TOKENS.orderService());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType("application/json").content(body);
    }

    private static String lines(String... bodyLines) {
        return "{\"lines\":[" + String.join(",", bodyLines) + "]}";
    }

    private static String line(int book, int quantity) {
        return line(book, quantity, 4900, true);
    }

    private static String line(int book, int quantity, long price, boolean pricing) {
        return "{\"bookId\":\"" + book(book) + "\",\"quantity\":" + quantity
                + (pricing ? ",\"unitPriceMinor\":" + price + ",\"currency\":\"CNY\"" : "") + "}";
    }

    private static <T> List<T> race(Callable<T> a, Callable<T> b) throws Exception {
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var entered = new CountDownLatch(2);
            var first = threads.submit(() -> {
                entered.countDown();
                if (!entered.await(10, TimeUnit.SECONDS)) throw new AssertionError("Race timed out");
                return a.call();
            });
            var second = threads.submit(() -> {
                entered.countDown();
                if (!entered.await(10, TimeUnit.SECONDS)) throw new AssertionError("Race timed out");
                return b.call();
            });
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    static String book(int i) {
        return "00000000-0000-4000-8000-%012d".formatted(i);
    }
}
