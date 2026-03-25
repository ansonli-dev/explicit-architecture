package com.example.order.interfaces.event;

import com.example.order.application.command.order.AutoConfirmOrderCommand;
import com.example.order.domain.event.OrderPlaced;
import com.example.seedwork.application.bus.CommandBus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Business event listener (driving adapter) that triggers auto-confirmation
 * for small orders.
 *
 * <p>This listener is structurally identical to a REST controller: it receives
 * a trigger (domain event instead of HTTP request), translates it into a
 * Command, and dispatches via CommandBus. No business logic lives here —
 * the threshold and eligibility check are in the domain model.
 *
 * <p>Lives in {@code interfaces/event/} because it <b>drives</b> the
 * application layer, unlike infrastructure listeners (outbox, cache, ES sync)
 * which perform technical I/O themselves.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPlacedAutoConfirmListener {

    private static final long AUTO_CONFIRM_THRESHOLD_CENTS = 5000L;
    private static final String AUTO_CONFIRM_THRESHOLD_CURRENCY = "CNY";

    private final CommandBus commandBus;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlaced event) {
        log.debug("OrderPlaced received, dispatching auto-confirm check for orderId={}",
                event.orderId().value());
        commandBus.dispatch(new AutoConfirmOrderCommand(
                event.orderId().value(),
                AUTO_CONFIRM_THRESHOLD_CENTS,
                AUTO_CONFIRM_THRESHOLD_CURRENCY));
    }
}
