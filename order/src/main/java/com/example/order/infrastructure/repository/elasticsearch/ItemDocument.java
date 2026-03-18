package com.example.order.infrastructure.repository.elasticsearch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Represents one order item as stored in the {@code orders.items} ES field.
 *
 * <p>Field names use the snake_case convention written by Hibernate's Jackson serializer
 * into the PostgreSQL {@code jsonb} column, which Debezium CDC forwards verbatim to
 * Elasticsearch. The {@code @JsonProperty} annotations bridge snake_case storage to
 * camelCase Java fields.
 */
public record ItemDocument(
        UUID id,
        @JsonProperty("book_id")         UUID   bookId,
        @JsonProperty("book_title")       String bookTitle,
        @JsonProperty("unit_price_cents") long   unitPriceCents,
        String currency,
        int    quantity
) {}
