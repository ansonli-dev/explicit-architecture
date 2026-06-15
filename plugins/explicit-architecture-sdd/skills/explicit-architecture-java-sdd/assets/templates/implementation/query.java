package {{BASE_PACKAGE}}.application.query.{{FEATURE_PACKAGE}};

import {{QUERY_IMPORT}};

import java.util.UUID;

public record Get{{AGGREGATE}}Query(
    UUID aggregateId
) implements Query<{{AGGREGATE}}DetailResult> {}
