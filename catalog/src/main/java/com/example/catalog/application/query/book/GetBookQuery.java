package com.example.catalog.application.query.book;

import com.example.seedwork.application.query.Query;

import java.util.UUID;

public record GetBookQuery(UUID id) implements Query<BookDetailResponse> {
}
