package com.example.catalog.application.query.book;

import java.util.List;

public record ListBooksQuery(String category, int page, int size) {
    public static ListBooksQuery of(String category, int page, int size) {
        return new ListBooksQuery(category, page, size);
    }
}
