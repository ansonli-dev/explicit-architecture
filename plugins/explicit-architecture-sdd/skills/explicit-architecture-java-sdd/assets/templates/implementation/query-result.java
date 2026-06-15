package {{BASE_PACKAGE}}.application.query.{{FEATURE_PACKAGE}};

import java.time.Instant;
import java.util.UUID;

public record {{AGGREGATE}}DetailResult(
    UUID aggregateId,
    String status,
    Instant createdAt
) {}
