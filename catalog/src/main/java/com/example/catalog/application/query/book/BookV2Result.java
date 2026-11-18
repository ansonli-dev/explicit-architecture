package com.example.catalog.application.query.book;

import java.util.UUID;

public record BookV2Result(UUID id, String title, String author, String category, long priceMinor,
                           String currency, int stock, Long version, String coverUrl, String description) {
}
