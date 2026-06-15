package {{BASE_PACKAGE}}.application.command.{{FEATURE_PACKAGE}};

import {{BASE_PACKAGE}}.domain.model.{{AGGREGATE}}Id;
import {{BASE_PACKAGE}}.domain.model.{{ACTOR_ID_TYPE}};
import {{BASE_PACKAGE}}.domain.ports.{{AGGREGATE}}Persistence;
import {{COMMAND_HANDLER_IMPORT}};
import org.springframework.stereotype.Service;

@Service
public class {{ACTION}}{{AGGREGATE}}CommandHandler implements CommandHandler<{{ACTION}}{{AGGREGATE}}Command, {{ACTION}}{{AGGREGATE}}Result> {
    private final {{AGGREGATE}}Persistence persistence;

    public {{ACTION}}{{AGGREGATE}}CommandHandler({{AGGREGATE}}Persistence persistence) {
        this.persistence = persistence;
    }

    @Override
    public {{ACTION}}{{AGGREGATE}}Result handle({{ACTION}}{{AGGREGATE}}Command command) {
        var aggregate = persistence.getById(new {{AGGREGATE}}Id(command.aggregateId()));
        aggregate.{{BUSINESS_METHOD}}(new {{ACTOR_ID_TYPE}}(command.actorId()));
        persistence.save(aggregate);
        return new {{ACTION}}{{AGGREGATE}}Result(aggregate.id().value(), aggregate.status().name());
    }
}
