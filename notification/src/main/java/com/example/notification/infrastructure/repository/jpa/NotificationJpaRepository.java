package com.example.notification.infrastructure.repository.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {
    Page<NotificationJpaEntity> findByCustomerId(UUID customerId, Pageable pageable);
}
