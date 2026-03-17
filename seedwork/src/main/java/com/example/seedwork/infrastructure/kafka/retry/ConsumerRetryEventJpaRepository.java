package com.example.seedwork.infrastructure.kafka.retry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ConsumerRetryEventJpaRepository extends JpaRepository<ConsumerRetryEventJpaEntity, UUID> {

    /**
     * Sliding-window scan: due records that are not dead-lettered and under max attempts.
     * SKIP_LOCKED ensures concurrent scheduler instances don't double-process the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT r FROM ConsumerRetryEventJpaEntity r
            WHERE r.nextRetryAt <= :now
              AND r.attemptCount < :maxAttempts
              AND r.deadLettered = false
            ORDER BY r.nextRetryAt ASC
            LIMIT :batchSize
            """)
    List<ConsumerRetryEventJpaEntity> findDueForRetry(
            @Param("now") Instant now,
            @Param("maxAttempts") int maxAttempts,
            @Param("batchSize") int batchSize);

    boolean existsByEventId(UUID eventId);
}
