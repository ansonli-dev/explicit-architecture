package com.example.order.infrastructure.repository.elasticsearch;

import com.example.order.application.port.outbound.OrderSearchRepository;
import com.example.order.application.port.outbound.OrderSearchRepository.OrderProjection;
import com.example.order.application.query.order.OrderItemResponse;
import com.example.order.domain.event.OrderCancelled;
import com.example.order.domain.event.OrderConfirmed;
import com.example.order.domain.event.OrderPlaced;
import com.example.order.domain.event.OrderShipped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ES read-model projector — fires AFTER the write transaction commits.
 * Uses @TransactionalEventListener(phase=AFTER_COMMIT) so the ES write
 * is never part of the business transaction; a failure here does NOT
 * roll back the order write.  ES is eventually consistent by design.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderReadModelProjector {

    private final OrderSearchRepository orderSearchRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderPlaced event) {
        try {
            var items = event.items().stream()
                    .map(i -> new OrderItemResponse(
                            i.getBookId(), i.getBookTitle(),
                            i.getUnitPrice().cents(), i.getUnitPrice().currency(),
                            i.getQuantity()))
                    .toList();
            orderSearchRepository.save(new OrderProjection(
                    event.orderId().value(),
                    event.customerId().value(),
                    event.customerEmail(),
                    "PENDING",
                    event.totalAmount().cents(),
                    event.totalAmount().currency(),
                    items));
        } catch (Exception ex) {
            log.error("ES projection failed for OrderPlaced orderId={}", event.orderId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderConfirmed event) {
        try {
            orderSearchRepository.updateStatus(event.orderId().value(), "CONFIRMED");
        } catch (Exception ex) {
            log.error("ES projection failed for OrderConfirmed orderId={}", event.orderId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderShipped event) {
        try {
            orderSearchRepository.updateStatusWithTracking(
                    event.orderId().value(), "SHIPPED", event.trackingNumber());
        } catch (Exception ex) {
            log.error("ES projection failed for OrderShipped orderId={}", event.orderId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCancelled event) {
        try {
            orderSearchRepository.updateStatusWithReason(
                    event.orderId().value(), "CANCELLED", event.reason());
        } catch (Exception ex) {
            log.error("ES projection failed for OrderCancelled orderId={}", event.orderId(), ex);
        }
    }
}
