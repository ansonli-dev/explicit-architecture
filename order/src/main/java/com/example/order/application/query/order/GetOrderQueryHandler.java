package com.example.order.application.query.order;

import com.example.order.application.port.outbound.OrderReadRepository;
import com.example.order.application.port.outbound.OrderSearchRepository;
import com.example.order.domain.model.OrderId;
import com.example.seedwork.application.query.QueryHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GetOrderQueryHandler implements QueryHandler<GetOrderQuery, OrderDetailResponse> {

    private final OrderSearchRepository orderSearchRepository;
    private final OrderReadRepository orderReadRepository;

    public GetOrderQueryHandler(OrderSearchRepository orderSearchRepository,
                                OrderReadRepository orderReadRepository) {
        this.orderSearchRepository = orderSearchRepository;
        this.orderReadRepository = orderReadRepository;
    }

    @Override
    public OrderDetailResponse handle(GetOrderQuery query) {
        var fromEs = orderSearchRepository.findById(query.orderId());
        if (fromEs.isPresent()) {
            return fromEs.get();
        }

        log.warn("ES miss for orderId={}, falling back to PostgreSQL projection", query.orderId());
        // P-6: Read path → JPA projection, bypassing domain layer entirely
        return orderReadRepository.findDetailById(OrderId.of(query.orderId()))
                .orElseThrow(() -> new OrderNotFoundException(query.orderId()));
    }
}
