package com.example.order.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void multiply_overflow_throws_IllegalArgumentException() {
        Money money = new Money(Long.MAX_VALUE / 2 + 1, "CNY");
        assertThatThrownBy(() -> money.multiply(3))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("overflow");
    }
}
