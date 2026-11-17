package com.example.order.application.checkout;

import com.example.order.application.port.outbound.CheckoutCatalog;
import com.example.order.application.port.outbound.CheckoutStore;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.service.OrderPricingService;
import com.example.seedwork.application.command.Command;
import com.example.seedwork.application.command.CommandHandler;
import com.example.seedwork.application.query.Query;
import com.example.seedwork.application.query.QueryHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static com.example.order.application.checkout.CheckoutProblem.Code.*;

public final class CheckoutMessages {

    private CheckoutMessages() {}

    public record QuoteOrder(CustomerId owner, String email, List<Checkout.RequestedLine> lines)
            implements Command<Checkout.Quote> {}

    public record SubmitOrder(CustomerId owner, UUID quoteId, UUID idempotencyKey)
            implements Command<Checkout.Submission> {}

    public record GetSubmission(CustomerId owner, UUID submissionId) implements Query<Checkout.Submission> {}

    @Service
    @Profile("demo-integrated")
    @EnableConfigurationProperties(CheckoutIntakeProperties.class)
    static class QuoteOrderHandler implements CommandHandler<QuoteOrder, Checkout.Quote> {

        private final CheckoutCatalog catalog;
        private final CheckoutStore store;
        private final OrderPricingService pricing;
        private final CheckoutIntakeProperties intake;

        QuoteOrderHandler(CheckoutCatalog catalog, CheckoutStore store, OrderPricingService pricing,
                          CheckoutIntakeProperties intake) {
            this.catalog = catalog;
            this.store = store;
            this.pricing = pricing;
            this.intake = intake;
        }

        @Override
        public Checkout.Quote handle(QuoteOrder command) {
            if (!intake.intakeEnabled()) {
                throw new CheckoutProblem(INTAKE_CLOSED);
            }
            var lines = command.lines().stream()
                    .sorted(Comparator.comparing(line -> line.bookId().toString()))
                    .toList();
            var bookIds = new HashSet<UUID>();
            for (var line : lines) {
                if (!bookIds.add(line.bookId())) {
                    throw new CheckoutProblem(INVALID_REQUEST);
                }
            }

            var snapshots = new HashMap<UUID, Checkout.Snapshot>();
            for (var snapshot : catalog.snapshot(lines)) {
                if (snapshots.putIfAbsent(snapshot.bookId(), snapshot) != null) {
                    throw new CheckoutProblem(CATALOG_UNAVAILABLE);
                }
            }
            if (!snapshots.keySet().equals(bookIds)) {
                throw new CheckoutProblem(CATALOG_UNAVAILABLE);
            }

            var quotedLines = new ArrayList<Checkout.Line>(lines.size());
            long originalTotal = 0;
            for (var line : lines) {
                var snapshot = snapshots.get(line.bookId());
                if (snapshot.title() == null || snapshot.title().isBlank() || snapshot.quantity() != line.quantity()) {
                    throw new CheckoutProblem(CATALOG_UNAVAILABLE);
                }
                if (!Checkout.CURRENCY.equals(snapshot.currency())) {
                    throw new CheckoutProblem(UNSUPPORTED_CURRENCY);
                }
                if (snapshot.stock() < line.quantity()) {
                    throw new CheckoutProblem(INSUFFICIENT_STOCK);
                }
                if (snapshot.unitPriceMinor() < 0 || snapshot.unitPriceMinor() > (Checkout.MAX_MINOR - originalTotal) / line.quantity()) {
                    throw new CheckoutProblem(AMOUNT_OUT_OF_RANGE);
                }
                originalTotal += snapshot.unitPriceMinor() * line.quantity();
                quotedLines.add(new Checkout.Line(line.bookId(), snapshot.title(), line.quantity(), snapshot.unitPriceMinor()));
            }
            var totals = pricing.calculate(quotedLines.stream().map(Checkout.Line::toOrderItem).toList(), Checkout.CURRENCY);
            var quote = new Checkout.Quote(UUID.randomUUID(), Instant.now().plus(10, ChronoUnit.MINUTES),
                    List.copyOf(quotedLines), totals.finalTotal().cents(), Checkout.CURRENCY);
            store.saveQuote(command.owner(), command.email(), quote);
            return quote;
        }
    }

    @Service
    @Profile("demo-integrated")
    record SubmitOrderHandler(CheckoutStore store, CheckoutIntakeProperties intake)
            implements CommandHandler<SubmitOrder, Checkout.Submission> {
        @Override
        public Checkout.Submission handle(SubmitOrder command) {
            return store.accept(command.owner(), command.quoteId(), command.idempotencyKey(), intake.intakeEnabled());
        }
    }

    @Service
    @Profile("demo-integrated")
    record GetSubmissionHandler(CheckoutStore store) implements QueryHandler<GetSubmission, Checkout.Submission> {
        @Override
        public Checkout.Submission handle(GetSubmission query) {
            return store.get(query.owner(), query.submissionId());
        }
    }
}
