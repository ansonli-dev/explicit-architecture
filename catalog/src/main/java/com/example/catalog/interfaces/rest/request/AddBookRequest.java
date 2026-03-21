package com.example.catalog.interfaces.rest.request;

public record AddBookRequest(
        String title,
        String authorName,
        String authorBiography,
        long priceCents,
        String currency,
        String categoryName,
        int initialStock) {}
