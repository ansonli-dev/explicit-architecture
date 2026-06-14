package {{BASE_PACKAGE}}.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class {{AGGREGATE}}CacheAdapterTest {
    @Container
    @ServiceConnection
    static GenericContainer<?> cache = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @Autowired
    {{AGGREGATE}}CacheAdapter adapter;

    @Test
    void stores_and_evicts_cached_value() {
        var aggregateId = UUID.randomUUID();
        var summary = new {{AGGREGATE}}Summary(aggregateId, "{{STATUS}}");

        adapter.put(summary, Duration.ofMinutes(5));

        assertThat(adapter.get(aggregateId)).contains(summary);

        adapter.evict(aggregateId);

        assertThat(adapter.get(aggregateId)).isEmpty();
    }
}
