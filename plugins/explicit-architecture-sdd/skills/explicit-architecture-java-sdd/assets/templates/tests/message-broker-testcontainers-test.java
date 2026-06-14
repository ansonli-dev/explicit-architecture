package {{BASE_PACKAGE}}.infrastructure.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class {{EVENT_NAME}}PublisherTest {
    @Container
    @ServiceConnection
    static KafkaContainer broker = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Autowired
    {{EVENT_NAME}}Publisher publisher;

    @Test
    void publishes_event_with_contract_headers() {
        var eventId = UUID.randomUUID().toString();

        publisher.publish(eventId, UUID.randomUUID());

        var props = Map.<String, Object>of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG, "{{EVENT_NAME}}PublisherTest",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );

        try (var consumer = new KafkaConsumer<String, String>(props)) {
            consumer.subscribe(List.of("{{TOPIC_NAME}}"));
            var records = consumer.poll(Duration.ofSeconds(10));

            assertThat(records).anySatisfy(record -> {
                assertThat(record.value()).contains(eventId);
                assertThat(record.headers().lastHeader("event-id")).isNotNull();
            });
        }
    }
}
