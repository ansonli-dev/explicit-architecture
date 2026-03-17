package com.example.catalog.domain.model;

public record Author(String name, String biography) {

    public Author {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Author name must not be blank");
        name = name.strip();
        biography = biography == null ? "" : biography.strip();
    }
}
