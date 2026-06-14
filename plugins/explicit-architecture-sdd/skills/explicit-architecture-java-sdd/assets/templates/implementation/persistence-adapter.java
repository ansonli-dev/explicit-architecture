package {{BASE_PACKAGE}}.infrastructure.persistence;

import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}};
import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Id;
import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Status;
import {{BASE_PACKAGE}}.domain.ports.{{AGGREGATE}}Repository;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Entity
@Table(name = "{{TABLE_NAME}}")
class {{AGGREGATE}}JpaEntity {
    @Id
    private UUID id;
    private String status;

    protected {{AGGREGATE}}JpaEntity() {}

    {{AGGREGATE}}JpaEntity(UUID id, String status) {
        this.id = id;
        this.status = status;
    }

    UUID getId() {
        return id;
    }

    String getStatus() {
        return status;
    }
}

interface {{AGGREGATE}}JpaRepository extends JpaRepository<{{AGGREGATE}}JpaEntity, UUID> {}

@Component
class {{AGGREGATE}}Mapper {
    {{AGGREGATE}}JpaEntity toEntity({{AGGREGATE}} aggregate) {
        return new {{AGGREGATE}}JpaEntity(aggregate.id().value(), aggregate.status().name());
    }

    {{AGGREGATE}} toDomain({{AGGREGATE}}JpaEntity entity) {
        return {{AGGREGATE}}.reconstitute(
            new {{AGGREGATE}}Id(entity.getId()),
            {{AGGREGATE}}Status.valueOf(entity.getStatus())
        );
    }
}

@Repository
public class {{AGGREGATE}}PersistenceAdapter implements {{AGGREGATE}}Repository {
    private final {{AGGREGATE}}JpaRepository jpaRepository;
    private final {{AGGREGATE}}Mapper mapper;

    public {{AGGREGATE}}PersistenceAdapter({{AGGREGATE}}JpaRepository jpaRepository, {{AGGREGATE}}Mapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public {{AGGREGATE}} getById({{AGGREGATE}}Id id) {
        return jpaRepository.findById(id.value())
            .map(mapper::toDomain)
            .orElseThrow();
    }

    @Override
    @Transactional
    public void save({{AGGREGATE}} aggregate) {
        jpaRepository.save(mapper.toEntity(aggregate));
    }
}
