package com.example.order.infrastructure.repository.elasticsearch.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Utility for deserializing a JSON string into a typed {@code List<T>}.
 *
 * <p>Debezium CDC serializes PostgreSQL {@code jsonb} columns as escaped JSON strings when
 * forwarding changes to Elasticsearch via the Sink Connector. Infrastructure adapters use
 * this utility to convert those strings into typed Java objects without duplicating the
 * Jackson deserialization logic.
 *
 * <p>Usage — subclass with one line per target type:
 * <pre>{@code
 * public class ItemDocumentParser extends JsonStringToListConverter<ItemDocument> {
 *     @Override protected TypeReference<List<ItemDocument>> typeReference() {
 *         return new TypeReference<>() {};
 *     }
 * }
 * }</pre>
 *
 * <p>Field name mapping: {@code @JsonProperty} annotations on the target record's constructor
 * parameters bridge the snake_case keys written by Hibernate/Debezium to camelCase Java fields.
 *
 * @param <T> the element type of the target list
 */
public abstract class JsonStringToListConverter<T> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(JsonStringToListConverter.class);

    protected abstract TypeReference<List<T>> typeReference();

    /**
     * Parses {@code json} into {@code List<T>}. Returns an empty list if {@code json} is blank
     * or cannot be parsed.
     */
    public List<T> parse(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, typeReference());
        } catch (Exception e) {
            log.warn("Failed to parse JSON string into list (type={}): {}", typeReference().getType(), e.getMessage());
            return List.of();
        }
    }
}
