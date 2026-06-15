package {{BASE_PACKAGE}}.infrastructure.repository.jpa;

import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}};
import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Id;
import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@Import({ {{AGGREGATE}}PersistenceAdapter.class, {{AGGREGATE}}Mapper.class })
class {{AGGREGATE}}PersistenceAdapterTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    {{AGGREGATE}}PersistenceAdapter adapter;

    @Autowired
    {{AGGREGATE}}JpaRepository jpaRepository;

    @Test
    void saves_and_loads_aggregate_against_real_database() {
        var aggregateId = new {{AGGREGATE}}Id(UUID.randomUUID());
        var aggregate = {{AGGREGATE}}.create(aggregateId);

        adapter.save(aggregate);

        var entity = jpaRepository.findById(aggregateId.value()).orElseThrow();
        assertThat(entity.getId()).isEqualTo(aggregateId.value());
        assertThat(entity.getStatus()).isEqualTo({{AGGREGATE}}Status.CREATED.name());

        var loaded = adapter.getById(aggregateId);
        assertThat(loaded.id()).isEqualTo(aggregateId);
        assertThat(loaded.status()).isEqualTo({{AGGREGATE}}Status.CREATED);
    }
}
