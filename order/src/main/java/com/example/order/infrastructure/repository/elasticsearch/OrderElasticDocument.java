package com.example.order.infrastructure.repository.elasticsearch;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "orders")
@Getter
@Setter
public class OrderElasticDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String customerId;

    @Field(type = FieldType.Keyword)
    private String customerEmail;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Long)
    private long totalCents;

    @Field(type = FieldType.Keyword)
    private String currency;

    /**
     * Order items serialized as a JSON string by the Elasticsearch Sink Connector.
     * Debezium forwards the PostgreSQL {@code jsonb} column verbatim as an escaped JSON string.
     * {@link com.example.order.infrastructure.repository.elasticsearch.converter.ItemDocumentListConverter}
     * is used by the adapter to parse this string into typed {@code List<ItemDocument>}.
     */
    @Field(type = FieldType.Text, index = false)
    private String items;

    @Field(type = FieldType.Keyword)
    private String trackingNumber;

    @Field(type = FieldType.Keyword)
    private String cancelReason;
}
