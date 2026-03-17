package com.example.order.infrastructure.repository.jpa;

import com.example.order.application.port.outbound.OrderPersistence;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
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
                .map(i -> new OrderItem(i.getId(), i.getBookId(), i.getBookTitle(),
                        new Money(i.getUnitPriceCents(), i.getCurrency()), i.getQuantity()))
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
        List<OrderItemJpaEntity> itemEntities = order.getItems().stream().map(i -> {
            OrderItemJpaEntity ie = new OrderItemJpaEntity();
            ie.setId(i.getId());
            ie.setBookId(i.getBookId());
            ie.setBookTitle(i.getBookTitle());
            ie.setUnitPriceCents(i.getUnitPrice().cents());
            ie.setCurrency(i.getUnitPrice().currency());
            ie.setQuantity(i.getQuantity());
            ie.setOrder(entity);
            return ie;
        }).toList();
        entity.setItems(itemEntities);
        entity.attachDomainEvents(order.pullDomainEvents());
        return entity;
    }
}
