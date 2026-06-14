package {{BASE_PACKAGE}}.application.command.{{FEATURE_PACKAGE}};

import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Id;
import {{BASE_PACKAGE}}.domain.model.{{ACTOR_ID_TYPE}};
import {{BASE_PACKAGE}}.domain.ports.{{AGGREGATE}}Repository;
import org.springframework.stereotype.Service;

import java.util.UUID;

public record {{ACTION}}{{AGGREGATE}}Command(UUID aggregateId, UUID actorId) {}

public record {{ACTION}}{{AGGREGATE}}Result(UUID aggregateId, String status) {}

@Service
public class {{ACTION}}{{AGGREGATE}}CommandHandler {
    private final {{AGGREGATE}}Repository repository;

    public {{ACTION}}{{AGGREGATE}}CommandHandler({{AGGREGATE}}Repository repository) {
        this.repository = repository;
    }

    public {{ACTION}}{{AGGREGATE}}Result handle({{ACTION}}{{AGGREGATE}}Command command) {
        var aggregate = repository.getById(new {{AGGREGATE}}Id(command.aggregateId()));
        aggregate.{{BUSINESS_METHOD}}(new {{ACTOR_ID_TYPE}}(command.actorId()));
        repository.save(aggregate);
        return new {{ACTION}}{{AGGREGATE}}Result(aggregate.id().value(), aggregate.status().name());
    }
}
