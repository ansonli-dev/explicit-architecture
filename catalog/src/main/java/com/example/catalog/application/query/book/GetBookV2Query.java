package com.example.catalog.application.query.book;

import com.example.seedwork.application.query.Query;

import java.util.UUID;

public record GetBookV2Query(UUID id) implements Query<BookV2Result> {}
