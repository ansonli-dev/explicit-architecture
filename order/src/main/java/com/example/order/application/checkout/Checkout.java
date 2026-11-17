package com.example.order.application.checkout;

import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderItem;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Checkout {

    public static final String CURRENCY = "CNY";
    public static final long MAX_MINOR = 9_007_199_254_740_991L;

    private Checkout() {}

    public record RequestedLine(UUID bookId, int quantity) {}

    public record Snapshot(UUID bookId, String title, int quantity, long unitPriceMinor, String currency, int stock) {}

    public record Line(UUID bookId, String title, int quantity, long unitPriceMinor) {
        public OrderItem toOrderItem() {
            return OrderItem.create(bookId, title, new Money(unitPriceMinor, CURRENCY), quantity);
        }
    }

    public record Quote(UUID quoteId, Instant expiresAt, List<Line> lines, long totalMinor, String currency) {}

    public enum State { PROCESSING, PLACED, REJECTED, RECONCILE_REQUIRED }

    public enum Rejection { PRICE_CHANGED, INSUFFICIENT_STOCK, BOOK_NOT_FOUND, PLACEMENT_ABORTED }

    public enum ReservationState { HELD, REJECTED, RELEASED }

    public record Reservation(ReservationState state, Rejection reason) {
        public Reservation {
            if (state == null || (state == ReservationState.REJECTED) != (reason != null)
                    || reason == Rejection.PLACEMENT_ABORTED) {
                throw new IllegalArgumentException("Invalid Catalog reservation decision");
            }
        }
    }

    public record PlacedOrder(UUID orderId, String status, long totalMinor, String currency) {}

    public record Submission(UUID submissionId, State state, PlacedOrder order, Rejection reason) {}
}
