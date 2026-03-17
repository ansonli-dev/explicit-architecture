package com.example.catalog.application.query.book;

import com.example.seedwork.application.query.Query;

import java.util.List;

public record ListBooksQuery(String category, int page, int size) implements Query<List<BookResponse>> {
    public static ListBooksQuery of(String category, int page, int size) {
        return new ListBooksQuery(category, page, size);
    }
}
