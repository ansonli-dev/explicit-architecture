package com.example.seedwork.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for the generic outbox table.
 * Services include this via component scan or explicit bean registration.
 */
public interface OutboxJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {

    @Query("SELECT e FROM OutboxEventJpaEntity e WHERE e.published = false ORDER BY e.createdAt ASC")
    List<OutboxEventJpaEntity> findUnpublished();

    @Modifying
    @Query("UPDATE OutboxEventJpaEntity e SET e.published = true WHERE e.id = :id")
    void markPublished(UUID id);

    long countByPublishedFalse();
}
