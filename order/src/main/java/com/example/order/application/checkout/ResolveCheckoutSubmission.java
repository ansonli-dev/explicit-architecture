package com.example.order.application.checkout;

import com.example.order.application.port.outbound.CheckoutCatalog;
import com.example.order.application.port.outbound.CheckoutStore;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("demo-integrated")
public class ResolveCheckoutSubmission {

    private static final Logger log = LoggerFactory.getLogger(ResolveCheckoutSubmission.class);
    private final CheckoutStore store;
    private final CheckoutCatalog catalog;

    public ResolveCheckoutSubmission(CheckoutStore store, CheckoutCatalog catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    public void processNext() {
        store.claimDue().ifPresent(this::resolve);
    }

    private void resolve(CheckoutStore.Claim claim) {
        try {
            if (claim.compensating()) {
                catalog.release(claim.id());
                store.reject(claim, Checkout.Rejection.PLACEMENT_ABORTED);
                return;
            }
            var decision = catalog.reserve(claim.id(), claim.lines());
            switch (decision.state()) {
                case HELD -> place(claim);
                case REJECTED -> store.reject(claim, decision.reason());
                case RELEASED -> store.reject(claim, Checkout.Rejection.PLACEMENT_ABORTED);
            }
        } catch (RuntimeException failure) {
            if (store.retry(claim)) {
                String kind = failure instanceof CheckoutProblem problem ? problem.code().name() : failure.getClass().getSimpleName();
                log.warn("Checkout submission {} attempt {} failed ({})", claim.id(), claim.attempt(), kind);
                if (claim.attempt() >= CheckoutStore.MAX_ATTEMPTS && !claim.compensating()) {
                    log.warn("Checkout submission {} needs reconciliation", claim.id());
                }
            }
        }
    }

    private void place(CheckoutStore.Claim claim) {
        var order = Order.createWithCheckoutReservation(OrderId.of(claim.id()), claim.owner(), claim.email(),
                claim.lines().stream().map(Checkout.Line::toOrderItem).toList(), new Money(claim.totalMinor(), Checkout.CURRENCY));
        order.place();
        store.place(claim, order);
    }
}
