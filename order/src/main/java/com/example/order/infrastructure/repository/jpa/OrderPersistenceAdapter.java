package com.example.order.infrastructure.repository.jpa;

import com.example.order.application.checkout.Checkout;
import com.example.order.application.checkout.CheckoutProblem;
import com.example.order.application.port.outbound.MyOrderReadRepository;
import com.example.order.domain.ports.OrderPersistence;
import com.example.order.application.port.outbound.OrderReadRepository;
import com.example.order.application.query.order.OrderDetailResult;
import com.example.order.application.query.order.OrderItemResult;
import com.example.order.application.query.order.MyOrders;
import com.example.order.domain.model.*;
import com.example.order.domain.event.OrderCancelled;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
class OrderPersistenceAdapter implements OrderPersistence, OrderReadRepository, MyOrderReadRepository {

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
                    e.setReservationProtocol(order.getReservationProtocol());
                    e.setStatus(order.getStatus().name());
                    e.setTotalCents(order.getTotalAmount().cents());
                    e.setCurrency(order.getTotalAmount().currency());
                    if (order.getStatus() instanceof OrderStatus.Shipped s) e.setTrackingNumber(s.trackingNumber());
                    if (order.getStatus() instanceof OrderStatus.Cancelled c) e.setCancelReason(c.reason());
                    e.setItems(itemJsons);
                    return e;
                });

        var events = order.peekDomainEvents();
        for (var event : events) {
            if (event instanceof OrderCancelled cancelled) {
                entity.setCancelledAt(cancelled.occurredAt().truncatedTo(ChronoUnit.MICROS));
            }
        }
        entity.attachDomainEvents(events);
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

    @Override
    @Transactional(readOnly = true)
    public MyOrders.PageResult listOwned(CustomerId owner, int page, int size, boolean includeLines) {
        var pageRequest = PageRequest.of(page - 1, size);
        Slice<MyOrders.SummaryResult> slice = includeLines
                ? orderJpaRepository.findOwnedWithLines(owner.value(), pageRequest).map(row -> new MyOrders.SummaryResult(
                        row.getId(), row.getStatus(), safeMinor(row.getTotalCents()), row.getCurrency(), row.getCreatedAt(), lines(row)))
                : orderJpaRepository.findOwnedSummaries(owner.value(), pageRequest).map(row -> new MyOrders.SummaryResult(
                        row.getId(), row.getStatus(), safeMinor(row.getTotalCents()), row.getCurrency(), row.getCreatedAt(), null));
        return new MyOrders.PageResult(slice.getContent(), page, page < 100 && slice.hasNext());
    }

    @Override
    @Transactional(readOnly = true)
    public MyOrders.DetailResult findOwned(CustomerId owner, OrderId id) {
        var entity = ownedOrder(owner, id);
        return new MyOrders.DetailResult(entity.getId(), entity.getStatus(), safeMinor(entity.getTotalCents()),
                entity.getCurrency(), entity.getCreatedAt(), entity.getVersion(), lines(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Cancellation findCancellation(CustomerId owner, OrderId id) {
        var entity = ownedOrder(owner, id);
        if (entity.getCancelledAt() == null || !"CANCELLED".equals(entity.getStatus())) {
            throw new CheckoutProblem(CheckoutProblem.Code.NOT_FOUND);
        }
        return cancellation(entity);
    }

    private OrderJpaEntity ownedOrder(CustomerId owner, OrderId id) {
        return orderJpaRepository.findByIdAndCustomerId(id.value(), owner.value())
                .orElseThrow(() -> new CheckoutProblem(CheckoutProblem.Code.NOT_FOUND));
    }

    @Override
    @Transactional
    public CancellationResult cancelOwned(OrderId id, CustomerId owner, String reason, long expectedVersion) {
        var entity = orderJpaRepository.lockOwnedOrder(id.value(), owner.value())
                .orElseThrow(() -> new CheckoutProblem(CheckoutProblem.Code.NOT_FOUND));
        if (Objects.equals(entity.getCancellationExpectedVersion(), expectedVersion) && reason.equals(entity.getCancelReason())) {
            return new CancellationResult(cancellation(entity), false);
        }
        if (entity.getVersion() != expectedVersion) {
            throw new CheckoutProblem(CheckoutProblem.Code.VERSION_CONFLICT);
        }
        var order = toDomain(entity);
        try {
            order.cancelByCustomer(reason);
        } catch (OrderStateException e) {
            throw new CheckoutProblem(CheckoutProblem.Code.ORDER_STATE_CONFLICT);
        }
        entity.setCancellationExpectedVersion(expectedVersion);
        save(order);
        return new CancellationResult(cancellation(entity), true);
    }

    private static Cancellation cancellation(OrderJpaEntity entity) {
        return new Cancellation(entity.getId(), entity.getCancelReason(), entity.getCancelledAt());
    }

    private static List<Checkout.Line> lines(OrderJpaEntity entity) {
        return entity.getItems().stream()
                .map(item -> new Checkout.Line(item.bookId(), item.bookTitle(), item.quantity(), safeMinor(item.unitPriceCents())))
                .toList();
    }

    private static long safeMinor(long minor) {
        if (minor < 0 || minor > Checkout.MAX_MINOR) {
            throw new CheckoutProblem(CheckoutProblem.Code.AMOUNT_OUT_OF_RANGE);
        }
        return minor;
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
                new Money(entity.getTotalCents(), entity.getCurrency()), entity.getReservationProtocol());
    }

}

