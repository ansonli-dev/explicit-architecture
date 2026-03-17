package com.example.catalog.infrastructure.repository.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BookJpaRepository extends JpaRepository<BookJpaEntity, UUID> {

    @Query("SELECT b FROM BookJpaEntity b JOIN FETCH b.category c " +
            "WHERE (:category IS NULL OR c.name = :category)")
    List<BookJpaEntity> findAllWithCategory(@Param("category") String category, Pageable pageable);
}
