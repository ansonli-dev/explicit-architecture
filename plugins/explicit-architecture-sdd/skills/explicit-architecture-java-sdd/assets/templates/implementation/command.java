package {{BASE_PACKAGE}}.application.command.{{FEATURE_PACKAGE}};

import {{COMMAND_IMPORT}};

import java.util.UUID;

public record {{ACTION}}{{AGGREGATE}}Command(
    UUID aggregateId,
    UUID actorId
) implements Command<{{ACTION}}{{AGGREGATE}}Result> {}
