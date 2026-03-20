package com.example.order.domain.service;

import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.PricingResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * OrderPricingService — Domain Service that applies bookstore pricing rules.
 * <p>
 * <h2>Why this is a Domain Service, not logic in Order or OrderItem</h2>
 * <ul>
 *   <li>{@code OrderItem} knows only its own price and quantity — it cannot
 *       see the full order context needed for order-level threshold discounts.</li>
 *   <li>{@code Order} is responsible for the order lifecycle state machine;
 *       embedding discount rules there would violate Single Responsibility.</li>
 *   <li>Pricing policy changes independently of order structure (e.g., seasonal
 *       promotions) — isolating it here makes that evolution trivial.</li>
 * </ul>
 *
 * <h2>Pricing rules (v1)</h2>
 * <ol>
 *   <li><b>Bulk discount</b>: any item with quantity &ge; 5 gets 10 % off its
 *       subtotal.</li>
 *   <li><b>Order threshold discount</b>: if the total after step 1 is &ge;
 *       ¥500 (50 000 fen), apply an additional 5 % off the whole order.</li>
 * </ol>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Stateless — safe to share; no mutable fields.</li>
 *   <li>Pure Java — zero Spring / JPA / IO imports.</li>
 *   <li>Takes domain objects, returns a domain value object.</li>
 * </ul>
 */
@Component
public class OrderPricingService {

    // ─── Rule constants ───────────────────────────────────────────────────────

    /** Minimum quantity per item line to qualify for bulk discount. */
    static final int BULK_DISCOUNT_MIN_QTY = 5;

    /** Percentage taken off an item subtotal when bulk threshold is met. */
    static final int BULK_DISCOUNT_PCT = 10;

    /** Order subtotal threshold (after item discounts, in cents) for the order-level discount. */
    static final long ORDER_THRESHOLD_CENTS = 50_000L; // ¥500

    /** Percentage taken off the whole order when threshold discount is triggered. */
    static final int ORDER_THRESHOLD_DISCOUNT_PCT = 5;

    // ─── Core calculation ─────────────────────────────────────────────────────

    /**
     * Calculates the final price for a set of order items, applying all
     * applicable pricing rules in sequence.
     *
     * @param items    non-empty list of order items; all must share {@code currency}
     * @param currency ISO 4217 currency code (e.g. "CNY")
     * @return a {@link PricingResult} with original total, discount amount,
     *         final total, and a human-readable list of applied discount labels
     * @throws IllegalArgumentException if items is null/empty
     */
    public PricingResult calculate(List<OrderItem> items, String currency) {
        if (items == null || items.isEmpty())
            throw new IllegalArgumentException("Cannot price an empty item list");

        List<String> appliedDiscounts = new ArrayList<>();

        // ── Step 1: item-level bulk discounts ─────────────────────────────────
        long originalTotalCents = 0;
        long afterBulkCents = 0;

        for (OrderItem item : items) {
            long itemSubtotalCents = item.subtotal().cents();
            originalTotalCents += itemSubtotalCents;

            if (item.quantity() >= BULK_DISCOUNT_MIN_QTY) {
                long discounted = applyPercent(itemSubtotalCents, BULK_DISCOUNT_PCT);
                afterBulkCents += discounted;
                appliedDiscounts.add(
                        "Bulk 10%% off \"%s\" (qty %d)".formatted(item.bookTitle(), item.quantity()));
            } else {
                afterBulkCents += itemSubtotalCents;
            }
        }

        // ── Step 2: order-level threshold discount ────────────────────────────
        long finalCents = afterBulkCents;

        if (afterBulkCents >= ORDER_THRESHOLD_CENTS) {
            finalCents = applyPercent(afterBulkCents, ORDER_THRESHOLD_DISCOUNT_PCT);
            appliedDiscounts.add("Order threshold 5%% off (subtotal ≥ ¥500)");
        }

        long discountCents = originalTotalCents - finalCents;

        return new PricingResult(
                new Money(originalTotalCents, currency),
                new Money(discountCents, currency),
                new Money(finalCents, currency),
                appliedDiscounts);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Returns {@code amount} reduced by {@code discountPct} percent, rounded down. */
    private static long applyPercent(long amount, int discountPct) {
        return amount * (100L - discountPct) / 100;
    }
}
