package com.example.catalog.application.command.book;

import com.example.catalog.application.port.outbound.BookCache;
import com.example.catalog.application.port.outbound.BookPersistence;
import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.domain.model.Author;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.StockLevel;
import com.example.catalog.domain.model.Title;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseStockCommandHandlerTest {

    @Mock BookPersistence bookRepository;
    @Mock BookCache bookCache;

    private ReleaseStockCommandHandler handler;
    private final UUID bookId = UUID.randomUUID();
    private Book existingBook;

    @BeforeEach
    void setUp() {
        handler = new ReleaseStockCommandHandler(bookRepository, bookCache);
        existingBook = Book.reconstitute(
                BookId.of(bookId),
                new Title("Clean Code"),
                new Author("Robert Martin", "Uncle Bob"),
                new Money(4500, "CNY"),
                new Category(UUID.randomUUID(), "Tech"),
                new StockLevel(10, 0));
    }

    @Test
    void givenPreviouslyReservedStock_whenHandle_thenStockRestoredAndEventRegistered() {
        // Arrange — pre-reserve so release is valid
        UUID orderId = UUID.randomUUID();
        existingBook.reserve(orderId, 5);
        existingBook.pullDomainEvents(); // clear events from reserve
        when(bookRepository.findById(BookId.of(bookId))).thenReturn(Optional.of(existingBook));

        // Act
        handler.handle(new ReleaseStockCommand(bookId, orderId, 5));

        // Assert
        verify(bookRepository).save(existingBook);
        assertThat(existingBook.pullDomainEvents()).hasSize(1);
        verify(bookCache).invalidate(bookId);
    }

    @Test
    void givenNonExistentBook_whenHandle_thenThrowsBookNotFoundException() {
        // Arrange
        when(bookRepository.findById(BookId.of(bookId))).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new ReleaseStockCommand(bookId, UUID.randomUUID(), 5)))
                .isInstanceOf(BookNotFoundException.class);
    }
}
