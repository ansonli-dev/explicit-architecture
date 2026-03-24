package com.example.order.infrastructure.repository.elasticsearch;

import com.example.order.application.port.outbound.OrderSearchRepository;
import com.example.order.application.query.order.OrderDetailResult;
import com.example.order.application.query.order.OrderItemResult;
import com.example.order.application.query.order.OrderSummaryResult;
import com.example.order.infrastructure.repository.elasticsearch.converter.ItemDocumentListConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
class OrderSearchAdapter implements OrderSearchRepository {

    private static final ItemDocumentListConverter ITEMS_CONVERTER = new ItemDocumentListConverter();

    private final OrderElasticRepository elasticRepository;

    OrderSearchAdapter(OrderElasticRepository elasticRepository) {
        this.elasticRepository = elasticRepository;
    }

    @Override
    public Optional<OrderDetailResult> findById(UUID orderId) {
        try {
            return elasticRepository.findById(orderId.toString()).map(this::toDetailResponse);
        } catch (Exception ex) {
            log.warn("ES unavailable for findById orderId={}, returning empty", orderId, ex);
            return Optional.empty();
        }
    }

    @Override
    public List<OrderSummaryResult> findByCustomerIdAndStatus(UUID customerId, String status, int page, int size) {
        try {
            var pageable = PageRequest.of(page, size);
            var results = status != null
                    ? elasticRepository.findByCustomerIdAndStatus(customerId.toString(), status, pageable)
                    : elasticRepository.findByCustomerId(customerId.toString(), pageable);
            return results.stream().map(this::toResponse).toList();
        } catch (Exception ex) {
            log.warn("ES unavailable for findByCustomerIdAndStatus customerId={}, returning empty", customerId, ex);
            return List.of();
        }
    }

    // --- Mapping helpers ---

    private OrderDetailResult toDetailResponse(OrderElasticDocument doc) {
        return new OrderDetailResult(
                UUID.fromString(doc.getId()),
                UUID.fromString(doc.getCustomerId()),
                doc.getCustomerEmail(),
                doc.getStatus(),
                ITEMS_CONVERTER.parse(doc.getItems()).stream()
                        .map(i -> new OrderItemResult(i.bookId(), i.bookTitle(), i.unitPriceCents(), i.currency(), i.quantity()))
                        .toList(),
                doc.getTotalCents(),
                doc.getCurrency());
    }

    private OrderSummaryResult toResponse(OrderElasticDocument doc) {
        return new OrderSummaryResult(
                UUID.fromString(doc.getId()),
                UUID.fromString(doc.getCustomerId()),
                doc.getStatus(),
                doc.getTotalCents(),
                doc.getCurrency());
    }
}
