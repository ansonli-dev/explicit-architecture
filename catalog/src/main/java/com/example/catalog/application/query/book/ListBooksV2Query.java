package com.example.catalog.application.query.book;

import com.example.seedwork.application.query.Query;

import java.util.List;

public record ListBooksV2Query(String category, String query, int page, int size)
        implements Query<ListBooksV2Query.Result> {
    public record Result(List<BookV2Result> books, int page, boolean hasNext) {}
}
