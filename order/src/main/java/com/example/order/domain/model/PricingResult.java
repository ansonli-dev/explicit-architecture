package com.example.order.domain.model;

import java.util.List;

/**
 * PricingResult — Value Object returned by OrderPricingService.
 * <p>
 * Captures the full pricing breakdown: what the customer would have paid
 * without discounts, how much was discounted, and the final amount to charge.
 * <p>
 * Immutable record; carries no identity.
 */
public record PricingResult(
        Money originalTotal,
        Money discountAmount,
        Money finalTotal,
        List<String> appliedDiscounts) {

    public PricingResult {
        if (!originalTotal.currency().equals(finalTotal.currency()))
            throw new IllegalArgumentException("Currency mismatch in PricingResult");
        appliedDiscounts = List.copyOf(appliedDiscounts); // defensive copy
    }

    public boolean hasDiscount() {
        return discountAmount.cents() > 0;
    }
}
