package com.example.order.infrastructure.repository.jpa;

import com.example.order.domain.ports.OrderPersistence;
import com.example.order.application.port.outbound.OrderReadRepository;
import com.example.order.application.query.order.OrderDetailResult;
import com.example.order.application.query.order.OrderItemResult;
import com.example.order.domain.model.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
class OrderPersistenceAdapter implements OrderPersistence, OrderReadRepository {

    private final OrderJpaRepository orderJpaRepository;

    OrderPersistenceAdapter(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        List<OrderJpaEntity.ItemJson> itemJsons = order.getItems().stream()
                .map(i -> new OrderJpaEntity.ItemJson(
                        i.id(), i.bookId(), i.bookTitle(),
                        i.unitPrice().cents(), i.unitPrice().currency(), i.quantity()))
                .toList();

        OrderJpaEntity entity = orderJpaRepository.findById(order.getId().value())
                .map(existing -> {
                    existing.setCustomerId(order.getCustomerId().value());
                    existing.setCustomerEmail(order.getCustomerEmail());
                    existing.setStatus(order.getStatus().name());
                    existing.setTotalCents(order.getTotalAmount().cents());
                    existing.setCurrency(order.getTotalAmount().currency());
                    existing.setTrackingNumber(order.getStatus() instanceof OrderStatus.Shipped s ? s.trackingNumber() : null);
                    existing.setCancelReason(order.getStatus() instanceof OrderStatus.Cancelled c ? c.reason() : null);
                    existing.setItems(itemJsons);
                    return existing;
                })
                .orElseGet(() -> {
                    OrderJpaEntity e = new OrderJpaEntity();
                    e.setId(order.getId().value());
                    e.setCustomerId(order.getCustomerId().value());
                    e.setCustomerEmail(order.getCustomerEmail());
                    e.setStatus(order.getStatus().name());
                    e.setTotalCents(order.getTotalAmount().cents());
                    e.setCurrency(order.getTotalAmount().currency());
                    if (order.getStatus() instanceof OrderStatus.Shipped s) e.setTrackingNumber(s.trackingNumber());
                    if (order.getStatus() instanceof OrderStatus.Cancelled c) e.setCancelReason(c.reason());
                    e.setItems(itemJsons);
                    return e;
                });

        entity.attachDomainEvents(order.peekDomainEvents());
        orderJpaRepository.save(entity);
        order.clearDomainEvents();
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(OrderId id) {
        return orderJpaRepository.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderDetailResult> findDetailById(OrderId id) {
        return orderJpaRepository.findById(id.value())
                .map(entity -> {
                    List<OrderItemResult> items = entity.getItems().stream()
                            .map(i -> new OrderItemResult(
                                    i.bookId(), i.bookTitle(),
                                    i.unitPriceCents(), i.currency(),
                                    i.quantity()))
                            .toList();
                    return new OrderDetailResult(
                            entity.getId(), entity.getCustomerId(),
                            entity.getCustomerEmail(), entity.getStatus(),
                            items, entity.getTotalCents(), entity.getCurrency());
                });
    }

    private Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(i.id(), i.bookId(), i.bookTitle(),
                        new Money(i.unitPriceCents(), i.currency()), i.quantity()))
                .toList();
        if (entity.getStatus() == null)
            throw new IllegalStateException("Order entity has null status: id=" + entity.getId());
        OrderStatus status = switch (entity.getStatus()) {
            case "PENDING"   -> new OrderStatus.Pending();
            case "PLACED"    -> new OrderStatus.Placed();
            case "CONFIRMED" -> new OrderStatus.Confirmed();
            case "SHIPPED"   -> new OrderStatus.Shipped(entity.getTrackingNumber());
            case "CANCELLED" -> new OrderStatus.Cancelled(entity.getCancelReason());
            default -> throw new IllegalStateException("Unknown order status: " + entity.getStatus());
        };
        return Order.reconstitute(OrderId.of(entity.getId()), CustomerId.of(entity.getCustomerId()),
                entity.getCustomerEmail(), status, items,
                new Money(entity.getTotalCents(), entity.getCurrency()));
    }

}

