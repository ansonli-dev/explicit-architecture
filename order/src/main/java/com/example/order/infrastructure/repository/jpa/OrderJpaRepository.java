package com.example.order.infrastructure.repository.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    String OWNED_PAGE = """
            from OrderJpaEntity o where o.customerId = :customerId
            order by o.createdAt desc nulls last, o.id desc
            """;

    @Query("""
            select o.id as id, o.status as status, o.totalCents as totalCents,
                   o.currency as currency, o.createdAt as createdAt
            """ + OWNED_PAGE)
    Slice<Summary> findOwnedSummaries(UUID customerId, Pageable page);

    @Query("select o " + OWNED_PAGE)
    Slice<OrderJpaEntity> findOwnedWithLines(UUID customerId, Pageable page);

    Optional<OrderJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderJpaEntity o WHERE o.id = :id AND o.customerId = :customerId")
    Optional<OrderJpaEntity> lockOwnedOrder(UUID id, UUID customerId);

    interface Summary {
        UUID getId();
        String getStatus();
        long getTotalCents();
        String getCurrency();
        Instant getCreatedAt();
    }
}
