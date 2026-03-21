package com.example.catalog.interfaces.dto;

public record AddBookRequest(
        String title,
        String authorName,
        String authorBiography,
        long priceCents,
        String currency,
        String categoryName,
        int initialStock) {}
