package com.example.order.domain.model;

/**
 * Money value object — reused from order bounded context (no sharing with
 * catalog).
 * DDD: bounded contexts may duplicate simple VOs.
 */
public record Money(long cents, String currency) {

    public Money {
        if (cents < 0)
            throw new IllegalArgumentException("Money amount must be non-negative");
        if (currency == null || currency.isBlank())
            throw new IllegalArgumentException("Currency must not be blank");
        currency = currency.toUpperCase().strip();
    }

    public static Money of(long cents, String currency) {
        return new Money(cents, currency);
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency))
            throw new IllegalArgumentException("Cannot add different currencies");
        return new Money(this.cents + other.cents, this.currency);
    }

    public Money multiply(int factor) {
        return new Money(this.cents * factor, this.currency);
    }
}
