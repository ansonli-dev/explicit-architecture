package com.example.catalog.application.query.book;

import com.example.seedwork.application.query.Query;

import java.util.UUID;

public record GetStockQuery(UUID id) implements Query<StockResponse> {
}
