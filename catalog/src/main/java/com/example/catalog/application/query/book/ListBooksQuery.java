package com.example.catalog.application.query.book;

import com.example.seedwork.application.query.Query;

import java.util.List;

public record ListBooksQuery(String category, String query, int page, int size)
        implements Query<ListBooksQuery.Result> {
    public record Result(List<BookReadResult> books, int page, boolean hasNext) {}
}
