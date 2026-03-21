package com.example.order.interfaces.rest;

import com.example.seedwork.application.bus.QueryBus;
import com.example.order.application.query.order.GetOrderQuery;
import com.example.order.application.query.order.ListOrdersQuery;
import com.example.order.application.query.order.OrderSummaryView;
import com.example.order.interfaces.rest.response.OrderDetailResponse;
import com.example.order.interfaces.rest.response.OrderSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
class OrderQueryController {

    private final QueryBus queryBus;

    @GetMapping("/{id}")
    OrderDetailResponse get(@PathVariable UUID id) {
        return OrderDetailResponse.from(queryBus.dispatch(new GetOrderQuery(id)));
    }

    @GetMapping
    List<OrderSummaryResponse> list(@RequestParam UUID customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<OrderSummaryView> views = queryBus.dispatch(new ListOrdersQuery(customerId, status, page, size));
        return views.stream().map(OrderSummaryResponse::from).toList();
    }
}
