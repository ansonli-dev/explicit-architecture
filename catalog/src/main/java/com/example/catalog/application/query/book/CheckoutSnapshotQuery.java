package com.example.catalog.application.query.book;

import com.example.seedwork.application.query.Query;

import java.util.List;
import java.util.UUID;

public record CheckoutSnapshotQuery(List<Line> lines) implements Query<List<CheckoutSnapshotQuery.Result>> {

    public record Line(UUID bookId, int quantity) {}

    public record Result(UUID bookId, String title, int quantity, long unitPriceMinor, String currency, int stock) {}
}
