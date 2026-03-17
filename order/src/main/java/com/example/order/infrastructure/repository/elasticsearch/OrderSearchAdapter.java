package com.example.order.infrastructure.repository.elasticsearch;

import com.example.order.application.query.order.OrderDetailResponse;
import com.example.order.application.query.order.OrderItemResponse;
import com.example.order.application.query.order.OrderResponse;
import com.example.order.application.port.outbound.OrderSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
class OrderSearchAdapter implements OrderSearchRepository {

    private final OrderElasticRepository elasticRepository;

    OrderSearchAdapter(OrderElasticRepository elasticRepository) {
        this.elasticRepository = elasticRepository;
    }

    // --- Read ---

    @Override
    public Optional<OrderDetailResponse> findById(UUID orderId) {
        try {
            return elasticRepository.findById(orderId.toString()).map(this::toDetailResponse);
        } catch (Exception ex) {
            log.warn("ES unavailable for findById orderId={}, returning empty", orderId, ex);
            return Optional.empty();
        }
    }

    @Override
    public List<OrderResponse> findByCustomerIdAndStatus(UUID customerId, String status, int page, int size) {
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

    // --- Write (projection side) ---

    @Override
    public void save(OrderProjection p) {
        var doc = new OrderElasticDocument();
        doc.setId(p.orderId().toString());
        doc.setCustomerId(p.customerId().toString());
        doc.setCustomerEmail(p.customerEmail());
        doc.setStatus(p.status());
        doc.setTotalCents(p.totalCents());
        doc.setCurrency(p.currency());
        doc.setItems(p.items().stream()
                .map(i -> new OrderElasticDocument.OrderItemDoc(
                        i.bookId().toString(), i.bookTitle(),
                        i.unitPriceCents(), i.currency(), i.quantity()))
                .toList());
        elasticRepository.save(doc);
    }

    @Override
    public void updateStatus(UUID orderId, String status) {
        elasticRepository.findById(orderId.toString()).ifPresent(doc -> {
            doc.setStatus(status);
            elasticRepository.save(doc);
        });
    }

    @Override
    public void updateStatusWithTracking(UUID orderId, String status, String trackingNumber) {
        elasticRepository.findById(orderId.toString()).ifPresent(doc -> {
            doc.setStatus(status);
            doc.setTrackingNumber(trackingNumber);
            elasticRepository.save(doc);
        });
    }

    @Override
    public void updateStatusWithReason(UUID orderId, String status, String cancelReason) {
        elasticRepository.findById(orderId.toString()).ifPresent(doc -> {
            doc.setStatus(status);
            doc.setCancelReason(cancelReason);
            elasticRepository.save(doc);
        });
    }

    // --- Mapping helpers ---

    private OrderDetailResponse toDetailResponse(OrderElasticDocument doc) {
        List<OrderItemResponse> items = doc.getItems() == null ? List.of()
                : doc.getItems().stream()
                        .map(i -> new OrderItemResponse(UUID.fromString(i.bookId()), i.bookTitle(),
                                i.unitPriceCents(), i.currency(), i.quantity()))
                        .toList();
        return new OrderDetailResponse(UUID.fromString(doc.getId()), UUID.fromString(doc.getCustomerId()),
                doc.getCustomerEmail(), doc.getStatus(), items, doc.getTotalCents(), doc.getCurrency());
    }

    private OrderResponse toResponse(OrderElasticDocument doc) {
        return new OrderResponse(UUID.fromString(doc.getId()), UUID.fromString(doc.getCustomerId()),
                doc.getStatus(), doc.getTotalCents(), doc.getCurrency());
    }
}
