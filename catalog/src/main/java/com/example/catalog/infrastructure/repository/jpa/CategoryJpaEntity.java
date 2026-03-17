package com.example.catalog.infrastructure.repository.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 200)
    private String name;

    public CategoryJpaEntity(UUID id, String name) {
        this.id = id;
        this.name = name;
    }
}
