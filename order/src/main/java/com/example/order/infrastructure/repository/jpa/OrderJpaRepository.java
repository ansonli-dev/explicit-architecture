package com.example.order.infrastructure.repository.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    Slice<Summary> findAllByCustomerIdOrderByIdDesc(UUID customerId, Pageable page);

    Optional<OrderJpaEntity> findByIdAndCustomerId(UUID id, UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderJpaEntity o WHERE o.id = :id AND o.customerId = :customerId")
    Optional<OrderJpaEntity> lockOwnedOrder(UUID id, UUID customerId);

    interface Summary {
        UUID getId();
        String getStatus();
        long getTotalCents();
        String getCurrency();
    }
}
