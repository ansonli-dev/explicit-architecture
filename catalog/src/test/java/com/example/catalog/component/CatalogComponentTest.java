package com.example.catalog.component;

import com.example.events.KafkaResourceConstants;
import com.example.catalog.application.port.outbound.BookCache;
import com.example.events.v1.StockReleased;
import com.example.events.v1.StockReserved;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaResourceConstants.TOPIC_STOCK_RESERVED,
                KafkaResourceConstants.TOPIC_STOCK_RELEASED
        }
)
@Sql(scripts = "/cleanup.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
class CatalogComponentTest {

    @LocalServerPort int port;

    @MockBean BookCache bookCache;

    @Autowired JdbcTemplate jdbc;
    @Autowired EmbeddedKafkaBroker embeddedKafka;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID createBook(String title, int stock) {
        return UUID.fromString(
                given().contentType(ContentType.JSON)
                        .body(Map.of(
                                "title", title,
                                "authorName", "Test Author",
                                "authorBiography", "Bio",
                                "priceCents", 4500,
                                "currency", "CNY",
                                "categoryName", "Tech",
                                "initialStock", stock))
                        .post("/api/v1/books")
                        .then().statusCode(201)
                        .extract().path("id"));
    }

    private KafkaConsumer<String, Object> avroConsumer(String... topics) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "test-consumer-" + UUID.randomUUID(), "false", embeddedKafka);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://test");
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(props);
        List<TopicPartition> partitions = List.of(topics).stream()
                .map(t -> new TopicPartition(t, 0))
                .toList();
        consumer.assign(partitions);
        consumer.seekToEnd(partitions);
        partitions.forEach(consumer::position);
        return consumer;
    }

    // ── REST API + PostgreSQL ─────────────────────────────────────────────────

    @Test
    void givenValidBookData_whenCreateBook_thenReturns201AndPersistsToDb() {
        // Act
        UUID id = createBook("Clean Code", 10);

        // Assert
        assertThat(id).isNotNull();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM book WHERE id = ?", Integer.class, id);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void givenExistingBook_whenGetBook_thenReturns200WithDetails() {
        // Arrange
        UUID id = createBook("Refactoring", 5);

        // Act + Assert
        given()
                .get("/api/v1/books/{id}", id)
                .then().statusCode(200);
    }

    @Test
    void givenNonExistentBookId_whenGetBook_thenReturns404() {
        given()
                .get("/api/v1/books/{id}", UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void givenBooksExist_whenListBooks_thenReturns200() {
        // Arrange
        createBook("Book A", 3);
        createBook("Book B", 7);

        // Act + Assert
        given()
                .get("/api/v1/books")
                .then().statusCode(200);
    }

    @Test
    void givenSufficientStock_whenReserveStock_thenAvailableStockReduced() {
        // Arrange
        UUID bookId = createBook("DDD", 10);
        UUID orderId = UUID.randomUUID();

        // Act
        given().contentType(ContentType.JSON)
                .body(Map.of("orderId", orderId, "quantity", 3))
                .post("/api/v1/books/{id}/stock/reserve", bookId)
                .then().statusCode(200);

        // Assert
        Integer reserved = jdbc.queryForObject(
                "SELECT stock_reserved FROM book WHERE id = ?", Integer.class, bookId);
        assertThat(reserved).isEqualTo(3);
    }

    @Test
    void givenInsufficientStock_whenReserveStock_thenReturns4xxAndStockUnchanged() {
        // Arrange
        UUID bookId = createBook("TDD", 2);
        UUID orderId = UUID.randomUUID();

        // Act + Assert
        given().contentType(ContentType.JSON)
                .body(Map.of("orderId", orderId, "quantity", 5))
                .post("/api/v1/books/{id}/stock/reserve", bookId)
                .then().statusCode(org.hamcrest.Matchers.greaterThanOrEqualTo(400));

        Integer reserved = jdbc.queryForObject(
                "SELECT stock_reserved FROM book WHERE id = ?", Integer.class, bookId);
        assertThat(reserved).isEqualTo(0);
    }

    @Test
    void givenReservedStock_whenReleaseStock_thenAvailableStockRestored() {
        // Arrange
        UUID bookId = createBook("CQRS", 10);
        UUID orderId = UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body(Map.of("orderId", orderId, "quantity", 4))
                .post("/api/v1/books/{id}/stock/reserve", bookId)
                .then().statusCode(200);

        // Act
        given().contentType(ContentType.JSON)
                .body(Map.of("orderId", orderId, "quantity", 4))
                .post("/api/v1/books/{id}/stock/release", bookId)
                .then().statusCode(204);

        // Assert
        Integer reserved = jdbc.queryForObject(
                "SELECT stock_reserved FROM book WHERE id = ?", Integer.class, bookId);
        assertThat(reserved).isEqualTo(0);
    }

    // ── Kafka event production ────────────────────────────────────────────────

    @Test
    void givenSufficientStock_whenReserveStock_thenStockReservedEventPublished() {
        // Arrange
        UUID bookId = createBook("Event Sourcing", 10);
        UUID orderId = UUID.randomUUID();

        try (KafkaConsumer<String, Object> consumer =
                     avroConsumer(KafkaResourceConstants.TOPIC_STOCK_RESERVED)) {

            // Act
            given().contentType(ContentType.JSON)
                    .body(Map.of("orderId", orderId, "quantity", 2))
                    .post("/api/v1/books/{id}/stock/reserve", bookId)
                    .then().statusCode(200);

            // Assert
            ConsumerRecord<String, Object> record =
                    KafkaTestUtils.getSingleRecord(consumer,
                            KafkaResourceConstants.TOPIC_STOCK_RESERVED,
                            Duration.ofSeconds(10));

            StockReserved event = (StockReserved) record.value();
            assertThat(event.getBookId().toString()).isEqualTo(bookId.toString());
            assertThat(event.getOrderId().toString()).isEqualTo(orderId.toString());
            assertThat(event.getQuantity()).isEqualTo(2);
        }
    }

    @Test
    void givenReservedStock_whenReleaseStock_thenStockReleasedEventPublished() {
        // Arrange
        UUID bookId = createBook("Microservices", 10);
        UUID orderId = UUID.randomUUID();
        given().contentType(ContentType.JSON)
                .body(Map.of("orderId", orderId, "quantity", 3))
                .post("/api/v1/books/{id}/stock/reserve", bookId)
                .then().statusCode(200);

        try (KafkaConsumer<String, Object> consumer =
                     avroConsumer(KafkaResourceConstants.TOPIC_STOCK_RELEASED)) {

            // Act
            given().contentType(ContentType.JSON)
                    .body(Map.of("orderId", orderId, "quantity", 3))
                    .post("/api/v1/books/{id}/stock/release", bookId)
                    .then().statusCode(204);

            // Assert
            ConsumerRecord<String, Object> record =
                    KafkaTestUtils.getSingleRecord(consumer,
                            KafkaResourceConstants.TOPIC_STOCK_RELEASED,
                            Duration.ofSeconds(10));

            StockReleased event = (StockReleased) record.value();
            assertThat(event.getBookId().toString()).isEqualTo(bookId.toString());
            assertThat(event.getQuantity()).isEqualTo(3);
        }
    }
}
