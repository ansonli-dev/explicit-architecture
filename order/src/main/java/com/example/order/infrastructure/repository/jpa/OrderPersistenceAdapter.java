package com.example.order.infrastructure.repository.jpa;

import com.example.order.application.port.outbound.OrderPersistence;
import com.example.order.domain.model.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
class OrderPersistenceAdapter implements OrderPersistence {

    private final OrderJpaRepository orderJpaRepository;

    OrderPersistenceAdapter(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        orderJpaRepository.save(toEntity(order));
        return order;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(OrderId id) {
        return orderJpaRepository.findById(id.value()).map(this::toDomain);
    }

    private Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(i.id(), i.bookId(), i.bookTitle(),
                        new Money(i.unitPriceCents(), i.currency()), i.quantity()))
                .toList();
        OrderStatus status = switch (entity.getStatus()) {
            case "PENDING"   -> new OrderStatus.Pending();
            case "CONFIRMED" -> new OrderStatus.Confirmed();
            case "SHIPPED"   -> new OrderStatus.Shipped(entity.getTrackingNumber());
            case "CANCELLED" -> new OrderStatus.Cancelled(entity.getCancelReason());
            default -> throw new IllegalStateException("Unknown order status: " + entity.getStatus());
        };
        return Order.reconstitute(OrderId.of(entity.getId()), CustomerId.of(entity.getCustomerId()),
                entity.getCustomerEmail(), status, items,
                new Money(entity.getTotalCents(), entity.getCurrency()));
    }

    private OrderJpaEntity toEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setId(order.getId().value());
        entity.setCustomerId(order.getCustomerId().value());
        entity.setCustomerEmail(order.getCustomerEmail());
        entity.setTotalCents(order.getTotalAmount().cents());
        entity.setCurrency(order.getTotalAmount().currency());
        entity.setStatus(order.getStatus().name());
        if (order.getStatus() instanceof OrderStatus.Shipped s)
            entity.setTrackingNumber(s.trackingNumber());
        if (order.getStatus() instanceof OrderStatus.Cancelled c)
            entity.setCancelReason(c.reason());
        List<OrderJpaEntity.ItemJson> items = order.getItems().stream()
                .map(i -> new OrderJpaEntity.ItemJson(
                        i.getId(), i.getBookId(), i.getBookTitle(),
                        i.getUnitPrice().cents(), i.getUnitPrice().currency(),
                        i.getQuantity()))
                .toList();
        entity.setItems(items);
        entity.attachDomainEvents(order.pullDomainEvents());
        return entity;
    }
}
