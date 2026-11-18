package com.example.catalog.infrastructure.repository.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface BookJpaRepository extends JpaRepository<BookJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BookJpaEntity b where b.id = :id")
    Optional<BookJpaEntity> lockById(@Param("id") UUID id);
}
