package com.example.order.infrastructure.repository.jpa;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "order_item")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private OrderJpaEntity order;

    @Column(name = "book_id", nullable = false)
    private UUID bookId;

    @Column(name = "book_title", nullable = false, length = 500)
    private String bookTitle;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
