package {{BASE_PACKAGE}}.interfaces.rest.response;

import java.util.UUID;

public record {{AGGREGATE}}{{PURPOSE}}Response(
    UUID aggregateId,
    String status
) {}
