package {{BASE_PACKAGE}}.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "{{TOPIC_NAME}}")
class {{EVENT_NAME}}PublisherEmbeddedTest {
    @Autowired
    {{EVENT_NAME}}Publisher publisher;

    @Test
    void publisher_can_send_to_embedded_broker() {
        assertThatCode(() -> publisher.publish("event-1", UUID.randomUUID()))
            .doesNotThrowAnyException();
    }
}
