package com.example.order.infrastructure.repository.jpa;

import com.example.seedwork.infrastructure.jpa.AbstractAggregateRootEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the orders table.
 * Extends {@link AbstractAggregateRootEntity} so that domain events attached
 * via {@code attachDomainEvents()} are published by Spring Data after {@code save()}.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderJpaEntity extends AbstractAggregateRootEntity {

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

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemJpaEntity> items = new ArrayList<>();
}
