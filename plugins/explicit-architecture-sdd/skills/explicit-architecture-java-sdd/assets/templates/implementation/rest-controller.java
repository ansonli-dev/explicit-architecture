package {{BASE_PACKAGE}}.interfaces.rest;

import {{BASE_PACKAGE}}.application.command.{{FEATURE_PACKAGE}}.{{ACTION}}{{AGGREGATE}}Command;
import {{BASE_PACKAGE}}.application.command.{{FEATURE_PACKAGE}}.{{ACTION}}{{AGGREGATE}}Result;
import {{BASE_PACKAGE}}.application.port.inbound.CommandBus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/{{RESOURCE_PATH}}")
public class {{AGGREGATE}}CommandController {
    private final CommandBus commandBus;

    public {{AGGREGATE}}CommandController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/{aggregateId}/{{ACTION_PATH}}")
    public ResponseEntity<{{ACTION}}{{AGGREGATE}}Response> {{ACTION_METHOD}}(
        @PathVariable UUID aggregateId,
        @Valid @RequestBody {{ACTION}}{{AGGREGATE}}Request request
    ) {
        {{ACTION}}{{AGGREGATE}}Result result = commandBus.dispatch(
            new {{ACTION}}{{AGGREGATE}}Command(aggregateId, request.actorId())
        );
        return ResponseEntity.ok(new {{ACTION}}{{AGGREGATE}}Response(result.aggregateId(), result.status()));
    }
}

record {{ACTION}}{{AGGREGATE}}Request(@NotNull UUID actorId) {}

record {{ACTION}}{{AGGREGATE}}Response(UUID aggregateId, String status) {}
