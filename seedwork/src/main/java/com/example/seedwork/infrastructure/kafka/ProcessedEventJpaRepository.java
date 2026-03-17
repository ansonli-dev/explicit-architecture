package com.example.seedwork.infrastructure.kafka;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProcessedEventJpaRepository extends JpaRepository<ProcessedEventJpaEntity, UUID> {
}
