package com.example.order.application.port.outbound;

import com.example.order.application.query.order.MyOrders;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.OrderId;

public interface MyOrderReadRepository {
    MyOrders.PageResult listOwned(CustomerId owner, int page, int size);
    MyOrders.DetailResult findOwned(CustomerId owner, OrderId id);
}
