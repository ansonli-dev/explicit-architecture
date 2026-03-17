package com.example.catalog.infrastructure.repository.jpa;

import com.example.seedwork.infrastructure.jpa.AbstractAggregateRootEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "book")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookJpaEntity extends AbstractAggregateRootEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "author_name", nullable = false, length = 200)
    private String authorName;

    @Column(name = "author_biography", columnDefinition = "TEXT")
    private String authorBiography;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryJpaEntity category;

    @Column(name = "stock_total", nullable = false)
    private int stockTotal;

    @Column(name = "stock_reserved", nullable = false)
    private int stockReserved;

    @Version
    @Column(name = "version")
    private Long version;
}
