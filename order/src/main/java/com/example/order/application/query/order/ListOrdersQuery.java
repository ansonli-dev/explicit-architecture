package com.example.order.application.query.order;

import com.example.seedwork.application.query.Query;

import java.util.List;
import java.util.UUID;

public record ListOrdersQuery(UUID customerId, String status, int page, int size)
        implements Query<List<OrderSummaryView>> {
}
