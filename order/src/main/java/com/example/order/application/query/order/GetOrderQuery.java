package com.example.order.application.query.order;

import com.example.seedwork.application.query.Query;

import java.util.UUID;

public record GetOrderQuery(UUID orderId) implements Query<OrderDetailView> {
}
