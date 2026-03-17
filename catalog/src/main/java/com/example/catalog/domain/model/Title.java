package com.example.catalog.domain.model;

public record Title(String value) {

    public Title {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Title must not be blank");
        if (value.length() > 500)
            throw new IllegalArgumentException("Title must not exceed 500 characters");
        value = value.strip();
    }

    @Override
    public String toString() {
        return value;
    }
}
