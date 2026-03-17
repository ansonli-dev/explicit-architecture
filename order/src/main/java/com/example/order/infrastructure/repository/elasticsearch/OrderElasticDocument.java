package com.example.order.infrastructure.repository.elasticsearch;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.List;

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

    @Field(type = FieldType.Nested)
    private List<OrderItemDoc> items;

    @Field(type = FieldType.Keyword)
    private String trackingNumber;

    @Field(type = FieldType.Keyword)
    private String cancelReason;

    public record OrderItemDoc(String bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {
    }
}
