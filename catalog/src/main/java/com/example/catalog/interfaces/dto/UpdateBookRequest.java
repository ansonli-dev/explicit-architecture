package com.example.catalog.interfaces.dto;

public record UpdateBookRequest(
        String title,
        String authorName,
        String authorBiography,
        Long priceCents,
        String currency,
        Integer restockQuantity) {}
