package com.example.order.application.command.order;

import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.ports.OrderPersistence;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoConfirmOrderCommandHandler implements CommandHandler<AutoConfirmOrderCommand, Void> {

    private final OrderPersistence orderRepository;

    @Override
    public Void handle(AutoConfirmOrderCommand command) {
        var orderId = OrderId.of(command.orderId());
        var threshold = Money.of(command.thresholdCents(), command.thresholdCurrency());

        orderRepository.findById(orderId).ifPresentOrElse(
                order -> {
                    if (order.confirmIfEligible(threshold)) {
                        orderRepository.save(order);
                        log.info("Order auto-confirmed: orderId={}, total={} <= threshold={}",
                                orderId.value(), order.getTotalAmount().cents(), threshold.cents());
                    } else {
                        log.debug("Order not eligible for auto-confirm: orderId={}, status={}, total={}",
                                orderId.value(), order.getStatus().name(), order.getTotalAmount().cents());
                    }
                },
                () -> log.warn("Order not found for auto-confirm: orderId={}", command.orderId())
        );
        return null;
    }
}
