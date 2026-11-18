package com.example.order.application.port.outbound;

import com.example.order.application.query.order.MyOrders;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.ports.OrderPersistence;

public interface MyOrderReadRepository {
    MyOrders.PageResult listOwned(CustomerId owner, int page, int size, boolean includeLines);
    MyOrders.DetailResult findOwned(CustomerId owner, OrderId id);
    OrderPersistence.Cancellation findCancellation(CustomerId owner, OrderId id);
}
