package {{BASE_PACKAGE}}.domain.event;

import {{DOMAIN_EVENT_IMPORT}};

import java.time.Instant;
import java.util.UUID;

public record {{EVENT_NAME}}(
    UUID eventId,
    UUID aggregateId,
    Instant occurredAt
) implements DomainEvent {}
