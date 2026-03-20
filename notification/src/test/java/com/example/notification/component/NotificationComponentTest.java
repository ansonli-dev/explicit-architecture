package com.example.notification.component;

import com.example.events.KafkaResourceConstants;
import com.example.events.v1.OrderCancelled;
import com.example.events.v1.OrderPlaced;
import com.example.notification.application.port.outbound.CustomerClient;
import com.example.seedwork.infrastructure.outbox.OutboxMapper;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.Sql.ExecutionPhase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Component test — validates the full notification flow end-to-end within this service boundary.
 * External Kafka is replaced with EmbeddedKafka. Database is real PostgreSQL via Testcontainers TC JDBC URL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaResourceConstants.TOPIC_ORDER_PLACED,
                KafkaResourceConstants.TOPIC_ORDER_CANCELLED
        }
)
@Sql(scripts = "/cleanup.sql", executionPhase = ExecutionPhase.BEFORE_TEST_METHOD)
class NotificationComponentTest {

    @MockBean OutboxMapper outboxMapper;
    // CustomerClient is an HTTP stub in prod; mock it here so the context starts without HTTP config
    // The mock returns Optional.empty() by default → notifications are saved as FAILED (acceptable in tests)
    @MockBean CustomerClient customerClient;

    @Autowired JdbcTemplate jdbc;
    @Autowired EmbeddedKafkaBroker embeddedKafka;

    // ── helpers ───────────────────────────────────────────────────────────────

    private KafkaTemplate<String, Object> avroProducer() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafka.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class,
                KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "mock://test",
                KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, true
        );
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private OrderPlaced buildOrderPlaced(String eventId, String customerId) {
        return OrderPlaced.newBuilder()
                .setEventId(eventId)
                .setOrderId(UUID.randomUUID().toString())
                .setCustomerId(customerId)
                .setCustomerEmail("test@example.com")
                .setItems(List.of())
                .setTotalCents(13500L)
                .setCurrency("CNY")
                .setOccurredAt(Instant.now())
                .build();
    }

    private OrderCancelled buildOrderCancelled(String eventId, String customerId) {
        return OrderCancelled.newBuilder()
                .setEventId(eventId)
                .setOrderId(UUID.randomUUID().toString())
                .setCustomerId(customerId)
                .setReason("customer request")
                .setOccurredAt(Instant.now())
                .build();
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void givenOrderPlacedEvent_whenConsumed_thenNotificationPersistedWithDeliveryStatus() {
        // Arrange
        String customerId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        OrderPlaced event = buildOrderPlaced(eventId, customerId);

        // Act
        avroProducer().send(new ProducerRecord<>(
                KafkaResourceConstants.TOPIC_ORDER_PLACED, eventId, event));

        // Assert — wait for async processing
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notification WHERE customer_id = ?",
                    Integer.class, UUID.fromString(customerId));
            assertThat(count).isGreaterThanOrEqualTo(1);
        });

        String status = jdbc.queryForObject(
                "SELECT delivery_status FROM notification WHERE customer_id = ?",
                String.class, UUID.fromString(customerId));
        assertThat(status).isIn("SENT", "FAILED");
    }

    @Test
    void givenOrderCancelledEvent_whenConsumed_thenNotificationPersisted() {
        // Arrange
        String customerId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        OrderCancelled event = buildOrderCancelled(eventId, customerId);

        // Act
        avroProducer().send(new ProducerRecord<>(
                KafkaResourceConstants.TOPIC_ORDER_CANCELLED, eventId, event));

        // Assert — wait for async processing
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notification WHERE customer_id = ?",
                    Integer.class, UUID.fromString(customerId));
            assertThat(count).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void givenDuplicateEvent_whenConsumedTwice_thenProcessedOnlyOnce() throws InterruptedException {
        // Arrange
        String customerId = UUID.randomUUID().toString();
        String eventId = UUID.randomUUID().toString();
        OrderPlaced event = buildOrderPlaced(eventId, customerId);
        KafkaTemplate<String, Object> producer = avroProducer();

        // Act — send first message and wait for it to be processed
        producer.send(new ProducerRecord<>(
                KafkaResourceConstants.TOPIC_ORDER_PLACED, eventId, event));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM notification WHERE customer_id = ?",
                    Integer.class, UUID.fromString(customerId));
            assertThat(count).isEqualTo(1);
        });

        // Act — send duplicate
        producer.send(new ProducerRecord<>(
                KafkaResourceConstants.TOPIC_ORDER_PLACED, eventId, event));
        Thread.sleep(2000);

        // Assert — still only one notification record
        Integer notificationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE customer_id = ?",
                Integer.class, UUID.fromString(customerId));
        assertThat(notificationCount).isEqualTo(1);

        // Assert — event recorded as processed exactly once
        Integer processedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Integer.class, UUID.fromString(eventId));
        assertThat(processedCount).isEqualTo(1);
    }
}
