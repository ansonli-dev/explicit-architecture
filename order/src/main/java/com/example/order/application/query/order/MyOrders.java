package com.example.order.application.query.order;

import com.example.order.application.checkout.Checkout;
import com.example.order.application.port.outbound.MyOrderReadRepository;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.ports.OrderPersistence;
import com.example.seedwork.application.query.Query;
import com.example.seedwork.application.query.QueryHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MyOrders {

    private MyOrders() {}

    public record ListPage(CustomerId owner, int page, int size, boolean includeLines) implements Query<PageResult> {}
    public record Detail(CustomerId owner, OrderId orderId) implements Query<DetailResult> {}
    public record GetCancellation(CustomerId owner, OrderId orderId) implements Query<OrderPersistence.Cancellation> {}

    public record SummaryResult(UUID orderId, String status, long totalMinor, String currency, Instant createdAt,
                                List<Checkout.Line> lines) {}
    public record PageResult(List<SummaryResult> orders, int page, boolean hasNext) {}
    public record DetailResult(UUID orderId, String status, long totalMinor, String currency, Instant createdAt, long version,
                               List<Checkout.Line> lines) {}

    @Service
    @Profile("demo-integrated")
    record ListPageHandler(MyOrderReadRepository orders) implements QueryHandler<ListPage, PageResult> {
        @Override
        public PageResult handle(ListPage query) {
            return orders.listOwned(query.owner(), query.page(), query.size(), query.includeLines());
        }
    }

    @Service
    @Profile("demo-integrated")
    record DetailHandler(MyOrderReadRepository orders) implements QueryHandler<Detail, DetailResult> {
        @Override
        public DetailResult handle(Detail query) {
            return orders.findOwned(query.owner(), query.orderId());
        }
    }

    @Service
    @Profile("demo-integrated")
    record GetCancellationHandler(MyOrderReadRepository orders) implements QueryHandler<GetCancellation, OrderPersistence.Cancellation> {
        @Override
        public OrderPersistence.Cancellation handle(GetCancellation query) {
            return orders.findCancellation(query.owner(), query.orderId());
        }
    }
}
