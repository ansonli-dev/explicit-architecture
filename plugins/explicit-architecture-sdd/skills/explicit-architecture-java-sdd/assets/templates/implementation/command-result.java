package {{BASE_PACKAGE}}.application.command.{{FEATURE_PACKAGE}};

import java.util.UUID;

public record {{ACTION}}{{AGGREGATE}}Result(
    UUID aggregateId,
    String status
) {}
