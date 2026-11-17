package com.example.order.application.command.order;

import com.example.order.application.checkout.CheckoutProblem;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.ports.OrderPersistence;
import com.example.seedwork.application.command.Command;
import com.example.seedwork.application.command.CommandHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

public record CancelOwnedOrderCommand(CustomerId owner, OrderId orderId, String reason, long expectedVersion)
        implements Command<Void> {

    @Service
    @Profile("demo-integrated")
    record Handler(OrderPersistence orders) implements CommandHandler<CancelOwnedOrderCommand, Void> {
        @Override
        public Void handle(CancelOwnedOrderCommand command) {
            String reason = command.reason().strip();
            if (reason.isEmpty() || reason.length() > 200) {
                throw new CheckoutProblem(CheckoutProblem.Code.INVALID_REQUEST);
            }
            orders.cancelOwned(command.orderId(), command.owner(), reason, command.expectedVersion());
            return null;
        }
    }
}
