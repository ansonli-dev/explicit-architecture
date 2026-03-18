package com.example.order.infrastructure.repository.elasticsearch.converter;

import com.example.order.infrastructure.repository.elasticsearch.ItemDocument;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;

/**
 * Parses a JSON string (as written by Debezium CDC for the {@code orders.items} jsonb column)
 * into {@code List<ItemDocument>}.
 */
public class ItemDocumentListConverter extends JsonStringToListConverter<ItemDocument> {

    @Override
    protected TypeReference<List<ItemDocument>> typeReference() {
        return new TypeReference<>() {};
    }
}
