package com.example.catalog.domain;

import com.example.catalog.domain.model.Author;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.InsufficientStockException;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.Title;
import com.example.catalog.domain.event.StockReserved;
import com.example.catalog.domain.event.StockReleased;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTest {

    private Book book;
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Category category = new Category(UUID.randomUUID(), "Software Engineering");
        book = Book.create(
                new Title("Domain-Driven Design"),
                new Author("Eric Evans", "DDD pioneer"),
                new Money(5000, "CNY"),
                category,
                10);
    }

    @Nested
    class Reserve {

        @Test
        void givenSufficientStock_whenReserve_thenStockUpdatedAndEventRegistered() {
            // Act
            book.reserve(orderId, 3);

            // Assert
            assertThat(book.getStockLevel().reserved()).isEqualTo(3);
            assertThat(book.getStockLevel().available()).isEqualTo(7);
            var events = book.pullDomainEvents();
            assertThat(events).hasSize(1);
            StockReserved event = (StockReserved) events.get(0);
            assertThat(event.quantity()).isEqualTo(3);
            assertThat(event.orderId()).isEqualTo(orderId);
            assertThat(event.eventId()).isNotNull();
        }

        @Test
        void givenInsufficientStock_whenReserve_thenThrowsAndStockUnchanged() {
            // Act & Assert
            assertThatThrownBy(() -> book.reserve(orderId, 11))
                    .isInstanceOf(InsufficientStockException.class);

            // Assert — reserved must not change on failed reserve
            assertThat(book.getStockLevel().reserved()).isEqualTo(0);
        }

        @Test
        void givenPartialReserve_whenCheckAvailable_thenRemainingIsCorrect() {
            // Act
            book.reserve(orderId, 4);

            // Assert
            assertThat(book.getStockLevel().available()).isEqualTo(6);
        }
    }

    @Nested
    class Release {

        @Test
        void givenPreviouslyReservedStock_whenRelease_thenStockRestoredAndEventRegistered() {
            // Arrange
            book.reserve(orderId, 5);
            book.pullDomainEvents(); // clear reserve event

            // Act
            book.release(orderId, 5);

            // Assert
            assertThat(book.getStockLevel().reserved()).isEqualTo(0);
            assertThat(book.getStockLevel().available()).isEqualTo(10);
            var events = book.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(((StockReleased) events.get(0)).quantity()).isEqualTo(5);
        }
    }

    @Nested
    class Restock {

        @Test
        void givenAdditionalQuantity_whenRestock_thenTotalStockIncreased() {
            // Act
            book.restock(5);

            // Assert
            assertThat(book.getStockLevel().total()).isEqualTo(15);
        }
    }

    @Nested
    class UpdatePrice {

        @Test
        void givenNewPrice_whenUpdatePrice_thenPriceChanged() {
            // Act
            book.updatePrice(new Money(6000, "CNY"));

            // Assert
            assertThat(book.getPrice().cents()).isEqualTo(6000);
        }

        @Test
        void givenNullPrice_whenUpdatePrice_thenThrowsIllegalArgument() {
            // Act & Assert
            assertThatThrownBy(() -> book.updatePrice(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
