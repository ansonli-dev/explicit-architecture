package com.example.order.application.command.order;

import com.example.seedwork.application.command.CommandHandler;
import com.example.order.application.port.outbound.CatalogClient;
import com.example.order.domain.ports.OrderPersistence;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.PricingResult;
import com.example.order.domain.service.OrderPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceOrderCommandHandler implements CommandHandler<PlaceOrderCommand, PlaceOrderResult> {

    private final OrderPersistence orderRepository;
    private final CatalogClient catalogClient;
    private final OrderPricingService orderPricingService;

    @Override
    public PlaceOrderResult handle(PlaceOrderCommand command) {
        log.info("Placing order for customerId={}", command.customerId());

        OrderId orderId = OrderId.generate();

        // A-2: reject duplicate bookIds — same book in multiple line items cannot share a
        // stock snapshot and would cause double-reservation.
        long distinctBooks = command.items().stream().map(PlaceOrderCommand.OrderItem::bookId).distinct().count();
        if (distinctBooks != command.items().size()) {
            throw new IllegalArgumentException(
                    "Duplicate bookId in order items is not allowed; merge quantities before submitting");
        }

        // P-8: Fetch all stock data first (external IO belongs in handler)
        Map<UUID, Integer> availableStock = new HashMap<>();
        for (var req : command.items()) {
            var stockCheck = catalogClient.checkStock(req.bookId());
            availableStock.put(req.bookId(), stockCheck.availableStock());
        }

        // Build domain items (immutable snapshots)
        List<OrderItem> items = command.items().stream()
                .map(req -> OrderItem.create(req.bookId(), req.bookTitle(),
                        new Money(req.unitPriceCents(), req.currency()), req.quantity()))
                .toList();

        // A-5: currency is now validated inside Order.create(); extract it here for pricing only.
        String currency = items.get(0).unitPrice().currency();

        // Calculate pricing
        PricingResult pricing = orderPricingService.calculate(items, currency);
        if (pricing.hasDiscount()) {
            log.info("Discounts applied for customerId={}: {} (saved {} fen)",
                    command.customerId(), pricing.appliedDiscounts(), pricing.discountAmount().cents());
        }

        // P-8: Domain validates stock sufficiency
        Order order = Order.create(orderId, CustomerId.of(command.customerId()), command.customerEmail(),
                items, pricing.finalTotal(), availableStock);

        // P-15: Reserve with rollback on partial failure
        // A-3: also covers order.place() and save() — if either throws, rollback reserved stock
        List<UUID> reservedBookIds = new ArrayList<>();
        try {
            for (OrderItem item : items) {
                catalogClient.reserveStock(item.bookId(), orderId.value(), item.quantity());
                reservedBookIds.add(item.bookId());
            }
            order.place();
            orderRepository.save(order);
        } catch (Exception e) {
            log.warn("Order placement failed — rolling back {} already-reserved items", reservedBookIds.size());
            for (UUID bookId : reservedBookIds) {
                OrderItem item = items.stream().filter(i -> i.bookId().equals(bookId)).findFirst().orElseThrow();
                try {
                    catalogClient.releaseStock(bookId, orderId.value(), item.quantity());
                } catch (Exception ex) {
                    log.warn("Failed to rollback reserved stock for bookId={}", bookId, ex);
                }
            }
            throw new IllegalStateException("Order placement failed: " + e.getMessage(), e);
        }

        log.info("Order placed: orderId={}", orderId);
        return toResult(order);
    }

    private PlaceOrderResult toResult(Order order) {
        List<PlaceOrderResult.Item> items = order.getItems().stream()
                .map(i -> new PlaceOrderResult.Item(
                        i.bookId(), i.bookTitle(),
                        i.unitPrice().cents(), i.unitPrice().currency(),
                        i.quantity()))
                .toList();
        return new PlaceOrderResult(
                order.getId().value(), order.getCustomerId().value(), order.getCustomerEmail(),
                order.getStatus().name(), items,
                order.getTotalAmount().cents(), order.getTotalAmount().currency());
    }
}
