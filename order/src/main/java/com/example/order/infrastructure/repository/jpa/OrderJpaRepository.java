package com.example.order.infrastructure.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {
}
