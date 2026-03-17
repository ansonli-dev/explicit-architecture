package com.example.order.application.command.order;

import com.example.seedwork.application.command.CommandHandler;
import com.example.order.application.port.outbound.CatalogClient;
import com.example.order.application.port.outbound.OrderPersistence;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.PricingResult;
import com.example.order.domain.service.OrderPricingService;
import com.example.order.application.query.order.OrderItemResponse;
import com.example.order.application.query.order.OrderDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceOrderCommandHandler implements CommandHandler<PlaceOrderCommand, OrderDetailResponse> {

    private final OrderPersistence orderRepository;
    private final CatalogClient catalogClient;
    private final OrderPricingService orderPricingService;

    @Override
    public OrderDetailResponse handle(PlaceOrderCommand command) {
        log.info("Placing order for customerId={}", command.customerId());

        OrderId orderId = OrderId.generate();

        List<OrderItem> items = command.items().stream().map(req -> {
            var stockCheck = catalogClient.checkStock(req.bookId());
            if (stockCheck.availableStock() < req.quantity()) {
                throw new IllegalStateException("Insufficient stock for book: " + req.bookId());
            }
            catalogClient.reserveStock(req.bookId(), orderId.value(), req.quantity());
            return OrderItem.create(req.bookId(), req.bookTitle(),
                    new Money(req.unitPriceCents(), req.currency()), req.quantity());
        }).toList();

        String currency = command.items().get(0).currency();
        PricingResult pricing = orderPricingService.calculate(items, currency);
        if (pricing.hasDiscount()) {
            log.info("Discounts applied for customerId={}: {} (saved {} fen)",
                    command.customerId(), pricing.appliedDiscounts(), pricing.discountAmount().cents());
        }

        Order order = Order.create(orderId, CustomerId.of(command.customerId()), command.customerEmail(),
                items, pricing.finalTotal());
        // place() registers OrderPlaced into the aggregate's event list.
        // The persistence adapter will pull it and write the outbox entry atomically.
        order.place();
        Order saved = orderRepository.save(order);

        log.info("Order placed: orderId={}", saved.getId());
        return toDetailResponse(saved);
    }

    private OrderDetailResponse toDetailResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getBookId(), i.getBookTitle(),
                        i.getUnitPrice().cents(), i.getUnitPrice().currency(), i.getQuantity()))
                .toList();
        return new OrderDetailResponse(
                order.getId().value(), order.getCustomerId().value(), order.getCustomerEmail(),
                order.getStatus().name(), itemResponses,
                order.getTotalAmount().cents(), order.getTotalAmount().currency());
    }
}
