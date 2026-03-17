package com.example.order.domain.service;

import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.PricingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OrderPricingService.
 * No Spring context, no mocks — pure domain logic tests.
 */
class OrderPricingServiceTest {

    private OrderPricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new OrderPricingService();
    }

    private static OrderItem item(String title, long unitPriceCents, int qty) {
        return new OrderItem(UUID.randomUUID(), UUID.randomUUID(), title,
                new Money(unitPriceCents, "CNY"), qty);
    }

    // ─── No discount ──────────────────────────────────────────────────────────

    @Nested
    class NoDiscount {

        @Test
        void givenSingleItemBelowBothThresholds_whenCalculate_thenNoDiscount() {
            // Arrange — ¥10 × 2 = ¥20; qty < 5 and total < ¥500
            var items = List.of(item("Spring in Action", 1_000, 2));

            // Act
            PricingResult result = pricingService.calculate(items, "CNY");

            // Assert
            assertThat(result.finalTotal().cents()).isEqualTo(2_000);
            assertThat(result.discountAmount().cents()).isZero();
            assertThat(result.appliedDiscounts()).isEmpty();
            assertThat(result.hasDiscount()).isFalse();
        }

        @Test
        void givenMultipleItemsAllBelowThresholds_whenCalculate_thenNoDiscount() {
            // Arrange — ¥50 × 3 + ¥20 × 2 = ¥190; no item meets qty >= 5, total < ¥500
            var items = List.of(
                    item("Book A", 5_000, 3),
                    item("Book B", 2_000, 2));

            // Act
            PricingResult result = pricingService.calculate(items, "CNY");

            // Assert
            assertThat(result.originalTotal().cents()).isEqualTo(19_000);
            assertThat(result.finalTotal().cents()).isEqualTo(19_000);
            assertThat(result.appliedDiscounts()).isEmpty();
        }
    }

    // ─── Bulk discount (qty >= 5, 10% off) ───────────────────────────────────

    @Nested
    class BulkDiscount {

        @Test
        void givenItemWithQtyExactlyFive_whenCalculate_thenBulkDiscountApplied() {
            // Arrange — ¥100 × 5 = ¥500; bulk 10% off → ¥450; total < threshold after discount → no threshold
            var items = List.of(item("DDD Book", 10_000, 5));

            // Act
            PricingResult result = pricingService.calculate(items, "CNY");

            // Assert
            assertThat(result.originalTotal().cents()).isEqualTo(50_000);
            assertThat(result.finalTotal().cents()).isEqualTo(45_000);
            assertThat(result.discountAmount().cents()).isEqualTo(5_000);
            assertThat(result.appliedDiscounts()).hasSize(1)
                    .allMatch(d -> d.contains("DDD Book"));
        }

        @Test
        void givenSixItemsBelowOrderThresholdAfterBulk_whenCalculate_thenOnlyBulkDiscountApplied() {
            // Arrange — ¥50 × 6 = ¥300; bulk 10% off → ¥270; ¥270 < ¥500 → no threshold discount
            var items = List.of(item("Clean Code", 5_000, 6));

            // Act
            PricingResult result = pricingService.calculate(items, "CNY");

            // Assert
            assertThat(result.originalTotal().cents()).isEqualTo(30_000);
            assertThat(result.finalTotal().cents()).isEqualTo(27_000);
            assertThat(result.appliedDiscounts()).hasSize(1);
        }

        @Test
        void givenMixedQualifyingAndNonQualifyingItems_whenCalculate_thenOnlyQualifyingGetBulkDiscount() {
            // Arrange — Book A qty=6 qualifies; Book B qty=2 does not
            var items = List.of(
                    item("Book A", 10_000, 6),  // 60_000 * 90% = 54_000
                    item("Book B", 5_000, 2));  // 10_000 unchanged

            // Act
            PricingResult result = pricingService.calculate(items, "CNY");

            // Assert — original: 70_000; after bulk on A: 64_000; 64_000 >= 50_000 → threshold 5% → 60_800
            assertThat(result.originalTotal().cents()).isEqualTo(70_000);
            assertThat(result.finalTotal().cents()).isEqualTo(60_800);
            assertThat(result.appliedDiscounts()).hasSize(2); // bulk + threshold
        }
    }

    // ─── Order threshold discount (total >= ¥500, 5% off) ────────────────────

    @Nested
    class OrderThresholdDiscount {

        @Test
        void givenTotalExactlyAtThreshold_whenCalculate_thenThresholdDiscountApplied() {
            // Arrange — ¥500 × 1 = ¥500; qty=1 → no bulk; total >= ¥500 → 5% off → ¥475
            var items = List.of(item("Expensive Book", 50_000, 1));

            // Act
            PricingResult result = pricingService.calculate(items, "CNY");

            // Assert
            assertThat(result.originalTotal().cents()).isEqualTo(50_000);
            assertThat(result.finalTotal().cents()).isEqualTo(47_500); // 50_000 * 95%
            assertThat(result.appliedDiscounts()).hasSize(1)
                    .allMatch(d -> d.contains("threshold"));
        }

        @Test
        void givenTotalJustBelowThreshold_whenCalculate_thenNoThresholdDiscount() {
            // Arrange — ¥499.99 = 49_999 cents; below threshold
            var items = List.of(item("Almost Expensive", 49_999, 1));

            // Act
            PricingResult result = pricingService.calculate(items, "CNY");

            // Assert
            assertThat(result.finalTotal().cents()).isEqualTo(49_999);
            assertThat(result.appliedDiscounts()).isEmpty();
        }
    }

    // ─── Combined discounts ───────────────────────────────────────────────────

    @Nested
    class CombinedDiscount {

        @Test
        void givenHighQtyExpensiveItems_whenCalculate_thenBothBulkAndThresholdApplied() {
            // Arrange — ¥200 × 5 = ¥1000; bulk 10% off → ¥900; ¥900 >= ¥500 → threshold 5% off → ¥855
            var items = List.of(item("Programming Book", 20_000, 5));

            // Act
            PricingResult result = pricingService.calculate(items, "CNY");

            // Assert
            assertThat(result.originalTotal().cents()).isEqualTo(100_000);
            assertThat(result.finalTotal().cents()).isEqualTo(85_500); // 90_000 * 95%
            assertThat(result.appliedDiscounts()).hasSize(2);
        }
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Nested
    class EdgeCases {

        @Test
        void givenEmptyItemList_whenCalculate_thenThrowsIllegalArgument() {
            // Act & Assert
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> pricingService.calculate(List.of(), "CNY"))
                    .withMessageContaining("empty");
        }

        @Test
        void givenNullItemList_whenCalculate_thenThrowsIllegalArgument() {
            // Act & Assert
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> pricingService.calculate(null, "CNY"));
        }

        @Test
        void givenCalculatedResult_whenModifyDiscountsList_thenThrowsUnsupportedOperation() {
            // Arrange
            var items = List.of(item("Book", 10_000, 1));
            PricingResult result = pricingService.calculate(items, "CNY");

            // Act & Assert
            assertThatThrownBy(() -> result.appliedDiscounts().add("hack"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
