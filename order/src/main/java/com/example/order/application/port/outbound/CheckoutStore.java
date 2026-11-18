package com.example.order.application.port.outbound;

import com.example.order.application.checkout.Checkout;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckoutStore {

    int MAX_ATTEMPTS = 6;

    void saveQuote(CustomerId owner, String email, Checkout.Quote quote);
    Checkout.Quote getQuote(CustomerId owner, UUID quoteId);
    Checkout.Submission accept(CustomerId owner, UUID quoteId, UUID idempotencyKey, boolean intakeEnabled);
    Checkout.Submission get(CustomerId owner, UUID submissionId);

    Optional<Claim> claimDue();
    boolean place(Claim claim, Order order);
    boolean reject(Claim claim, Checkout.Rejection reason);
    boolean retry(Claim claim);

    record Claim(UUID id, CustomerId owner, String email, List<Checkout.Line> lines, long totalMinor,
                 long fence, int attempt, boolean compensating) {}
}
