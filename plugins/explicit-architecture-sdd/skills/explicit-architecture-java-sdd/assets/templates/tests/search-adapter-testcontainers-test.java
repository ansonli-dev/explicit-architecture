package {{BASE_PACKAGE}}.infrastructure.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class {{AGGREGATE}}SearchAdapterTest {
    @Container
    @ServiceConnection
    static ElasticsearchContainer search = new ElasticsearchContainer(
        "docker.elastic.co/elasticsearch/elasticsearch:8.13.4"
    );

    @Autowired
    {{AGGREGATE}}SearchAdapter adapter;

    @Autowired
    {{AGGREGATE}}SearchRepository repository;

    @Test
    void finds_projection_by_status() {
        var aggregateId = UUID.randomUUID();
        repository.save(new {{AGGREGATE}}Document(aggregateId, "{{STATUS}}", "{{OWNER}}"));
        repository.refresh();

        var results = adapter.findByStatus("{{STATUS}}", 0, 20);

        assertThat(results)
            .extracting({{AGGREGATE}}SummaryResult::aggregateId)
            .contains(aggregateId);
    }
}
