package com.example.order.application.query.order;

import com.example.order.application.port.outbound.OrderPersistence;
import com.example.order.application.port.outbound.OrderSearchRepository;
import com.example.order.domain.model.OrderId;
import com.example.seedwork.application.query.QueryHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class GetOrderQueryHandler implements QueryHandler<GetOrderQuery, OrderDetailResponse> {

    private final OrderSearchRepository orderSearchRepository;
    private final OrderPersistence orderPersistence;

    public GetOrderQueryHandler(OrderSearchRepository orderSearchRepository,
                                OrderPersistence orderPersistence) {
        this.orderSearchRepository = orderSearchRepository;
        this.orderPersistence = orderPersistence;
    }

    @Override
    public OrderDetailResponse handle(GetOrderQuery query) {
        var fromEs = orderSearchRepository.findById(query.orderId());
        if (fromEs.isPresent()) {
            return fromEs.get();
        }

        log.warn("ES miss for orderId={}, falling back to PostgreSQL", query.orderId());
        return orderPersistence.findById(OrderId.of(query.orderId()))
                .map(this::toDetailResponse)
                .orElseThrow(() -> new OrderNotFoundException(query.orderId()));
    }

    private OrderDetailResponse toDetailResponse(com.example.order.domain.model.Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getBookId(), i.getBookTitle(),
                        i.getUnitPrice().cents(), i.getUnitPrice().currency(),
                        i.getQuantity()))
                .toList();
        return new OrderDetailResponse(
                order.getId().value(),
                order.getCustomerId().value(),
                order.getCustomerEmail(),
                order.getStatus().name(),
                items,
                order.getTotalAmount().cents(),
                order.getTotalAmount().currency());
    }
}
