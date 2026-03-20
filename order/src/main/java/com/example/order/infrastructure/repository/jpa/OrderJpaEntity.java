package com.example.order.infrastructure.repository.jpa;

import com.example.seedwork.infrastructure.jpa.AbstractAggregateRootEntity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderJpaEntity extends AbstractAggregateRootEntity {

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "items", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ItemJson> items = new ArrayList<>();

    /**
     * Item representation stored as JSONB in the orders table.
     * Field names use snake_case to match the column names in the CDC event
     * consumed by the Elasticsearch Sink Connector.
     */
    public record ItemJson(
            UUID id,
            @JsonProperty("book_id")         UUID   bookId,
            @JsonProperty("book_title")       String bookTitle,
            @JsonProperty("unit_price_cents") long   unitPriceCents,
            String currency,
            int    quantity
    ) {}
}
