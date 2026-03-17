package com.example.catalog.domain.model;

import java.util.UUID;

/**
 * Category entity — has identity, but lifecycle is owned by catalog bounded
 * context.
 */
public class Category {

    private final UUID id;
    private String name;

    public Category(UUID id, String name) {
        if (id == null)
            throw new IllegalArgumentException("Category id must not be null");
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Category name must not be blank");
        this.id = id;
        this.name = name.strip();
    }

    public static Category create(String name) {
        return new Category(UUID.randomUUID(), name);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank())
            throw new IllegalArgumentException("Category name must not be blank");
        this.name = newName.strip();
    }
}
