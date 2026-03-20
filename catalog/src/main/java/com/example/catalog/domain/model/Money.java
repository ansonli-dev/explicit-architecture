package com.example.catalog.domain.model;

/**
 * Monetary value in the smallest currency unit (e.g., cents for CNY/USD).
 * Immutable value object — no frameworks, pure Java.
 */
public record Money(long cents, String currency) {

    public Money {
        if (cents <= 0)
            throw new IllegalArgumentException("Money amount must be positive");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("Currency must not be blank");
        currency = currency.toUpperCase().strip();
    }

    public static Money of(long cents, String currency) {
        return new Money(cents, currency);
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot add money with different currencies: " + currency + " vs " + other.currency);
        }
        return new Money(this.cents + other.cents, this.currency);
    }

    public Money multiply(int factor) {
        try {
            return new Money(Math.multiplyExact(this.cents, factor), this.currency);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Money amount overflow when multiplying " + cents + " by " + factor);
        }
    }

    @Override
    public String toString() {
        return cents + " " + currency;
    }
}
