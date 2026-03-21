package com.example.order.application.query.order;

import com.example.order.application.port.outbound.OrderSearchRepository;
import com.example.seedwork.application.query.QueryHandler;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ListOrdersQueryHandler implements QueryHandler<ListOrdersQuery, List<OrderSummaryView>> {

    private final OrderSearchRepository orderSearchRepository;

    public ListOrdersQueryHandler(OrderSearchRepository orderSearchRepository) {
        this.orderSearchRepository = orderSearchRepository;
    }

    @Override
    public List<OrderSummaryView> handle(ListOrdersQuery query) {
        // ES unavailability is handled inside OrderSearchAdapter (returns empty list + logs warn)
        return orderSearchRepository.findByCustomerIdAndStatus(
                query.customerId(), query.status(), query.page(), query.size());
    }
}
