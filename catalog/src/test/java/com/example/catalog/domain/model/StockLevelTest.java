package com.example.catalog.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockLevelTest {

    @Nested
    class Construction {

        @Test
        void givenNegativeTotal_whenConstruct_thenThrowsIllegalArgument() {
            assertThatThrownBy(() -> new StockLevel(-1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        void givenNegativeReserved_whenConstruct_thenThrowsIllegalArgument() {
            assertThatThrownBy(() -> new StockLevel(10, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        void givenReservedExceedsTotal_whenConstruct_thenThrowsIllegalArgument() {
            assertThatThrownBy(() -> new StockLevel(5, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Reserved");
        }

        @Test
        void givenPositiveTotal_whenOf_thenReservedIsZero() {
            // Arrange + Act
            StockLevel stock = StockLevel.of(10);

            // Assert
            assertThat(stock.total()).isEqualTo(10);
            assertThat(stock.reserved()).isEqualTo(0);
        }
    }

    @Nested
    class Available {

        @Test
        void givenTotalAndReserved_whenAvailable_thenReturnsDifference() {
            // Arrange
            StockLevel stock = new StockLevel(10, 3);

            // Act + Assert
            assertThat(stock.available()).isEqualTo(7);
        }

        @Test
        void givenFullyReserved_whenAvailable_thenReturnsZero() {
            // Arrange
            StockLevel stock = new StockLevel(5, 5);

            // Act + Assert
            assertThat(stock.available()).isEqualTo(0);
        }
    }

    @Nested
    class CanReserve {

        @Test
        void givenZeroQuantity_whenCanReserve_thenReturnsFalse() {
            assertThat(StockLevel.of(10).canReserve(0)).isFalse();
        }

        @Test
        void givenNegativeQuantity_whenCanReserve_thenReturnsFalse() {
            assertThat(StockLevel.of(10).canReserve(-1)).isFalse();
        }

        @Test
        void givenEnoughStock_whenCanReserve_thenReturnsTrue() {
            // Arrange
            StockLevel stock = StockLevel.of(10);

            // Act + Assert
            assertThat(stock.canReserve(5)).isTrue();
        }

        @Test
        void givenExactAvailableStock_whenCanReserve_thenReturnsTrue() {
            // Arrange
            StockLevel stock = new StockLevel(10, 5);

            // Act + Assert
            assertThat(stock.canReserve(5)).isTrue();
        }

        @Test
        void givenInsufficientStock_whenCanReserve_thenReturnsFalse() {
            // Arrange
            StockLevel stock = new StockLevel(10, 8);

            // Act + Assert
            assertThat(stock.canReserve(5)).isFalse();
        }
    }

    @Nested
    class Reserve {

        @Test
        void givenZeroQuantity_whenReserve_thenThrowsIllegalArgument() {
            assertThatThrownBy(() -> StockLevel.of(10).reserve(0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void givenSufficientStock_whenReserve_thenReservedIncreased() {
            // Arrange
            StockLevel stock = StockLevel.of(10);

            // Act
            StockLevel result = stock.reserve(3);

            // Assert
            assertThat(result.reserved()).isEqualTo(3);
            assertThat(result.available()).isEqualTo(7);
            assertThat(result.total()).isEqualTo(10);
        }

        @Test
        void givenInsufficientStock_whenReserve_thenThrowsInsufficientStockException() {
            // Arrange
            StockLevel stock = new StockLevel(10, 8);

            // Act + Assert
            assertThatThrownBy(() -> stock.reserve(5))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("available=2")
                    .hasMessageContaining("requested=5");
        }

        @Test
        void givenOriginalStock_whenReserve_thenOriginalStockUnchanged() {
            // Arrange
            StockLevel stock = StockLevel.of(10);

            // Act
            stock.reserve(3);

            // Assert (record is immutable)
            assertThat(stock.reserved()).isEqualTo(0);
        }
    }

    @Nested
    class Release {

        @Test
        void givenZeroQuantity_whenRelease_thenThrowsIllegalArgument() {
            assertThatThrownBy(() -> StockLevel.of(10).release(0))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void givenReservedStock_whenRelease_thenReservedDecreased() {
            // Arrange
            StockLevel stock = new StockLevel(10, 5);

            // Act
            StockLevel result = stock.release(3);

            // Assert
            assertThat(result.reserved()).isEqualTo(2);
            assertThat(result.available()).isEqualTo(8);
        }

        @Test
        void givenReleaseMoreThanReserved_whenRelease_thenThrowsIllegalArgument() {
            // Arrange
            StockLevel stock = new StockLevel(10, 2);

            // Act + Assert
            assertThatThrownBy(() -> stock.release(5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot release more than reserved");
        }

        @Test
        void givenFullyReserved_whenReleaseAll_thenReservedIsZero() {
            // Arrange
            StockLevel stock = new StockLevel(10, 10);

            // Act
            StockLevel result = stock.release(10);

            // Assert
            assertThat(result.reserved()).isEqualTo(0);
            assertThat(result.available()).isEqualTo(10);
        }
    }

    @Nested
    class Restock {

        @Test
        void givenPositiveQuantity_whenRestock_thenTotalIncreased() {
            // Arrange
            StockLevel stock = new StockLevel(10, 3);

            // Act
            StockLevel result = stock.restock(5);

            // Assert
            assertThat(result.total()).isEqualTo(15);
            assertThat(result.reserved()).isEqualTo(3);
            assertThat(result.available()).isEqualTo(12);
        }

        @Test
        void givenZeroQuantity_whenRestock_thenThrowsIllegalArgument() {
            // Arrange
            StockLevel stock = StockLevel.of(10);

            // Act + Assert
            assertThatThrownBy(() -> stock.restock(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        void givenNegativeQuantity_whenRestock_thenThrowsIllegalArgument() {
            // Arrange
            StockLevel stock = StockLevel.of(10);

            // Act + Assert
            assertThatThrownBy(() -> stock.restock(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }
    }
}
