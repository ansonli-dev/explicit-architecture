package com.example.catalog.application.command.book;

import com.example.catalog.domain.ports.BookPersistence;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateBookCommandHandlerTest {

    @Mock BookPersistence bookRepository;

    private UpdateBookCommandHandler handler;
    private final UUID bookId = UUID.randomUUID();
    private Book existingBook;

    @BeforeEach
    void setUp() {
        handler = new UpdateBookCommandHandler(bookRepository);
        existingBook = Book.reconstitute(
                BookId.of(bookId),
                new Title("Clean Code"),
                new Author("Robert Martin", "Uncle Bob"),
                new Money(4500, "CNY"),
                new Category(UUID.randomUUID(), "Tech"),
                new StockLevel(10, 0));
    }

    @Test
    void givenExistingBookWithRestockQuantity_whenHandle_thenStockUpdated() {
        // Arrange
        when(bookRepository.findById(BookId.of(bookId))).thenReturn(Optional.of(existingBook));
        var command = new UpdateBookCommand(bookId, null, null, null, null, null, 5);

        // Act
        UpdateBookResult result = handler.handle(command);

        // Assert
        assertThat(result.availableStock()).isEqualTo(15);
        verify(bookRepository).save(existingBook);
    }

    @Test
    void givenExistingBookWithNewTitle_whenHandle_thenTitleUpdated() {
        // Arrange
        when(bookRepository.findById(BookId.of(bookId))).thenReturn(Optional.of(existingBook));
        var command = new UpdateBookCommand(bookId, "New Title", null, null, null, null, null);

        // Act
        UpdateBookResult result = handler.handle(command);

        // Assert
        assertThat(result.title()).isEqualTo("New Title");
    }

    @Test
    void givenExistingBookWithNewPrice_whenHandle_thenPriceUpdated() {
        // Arrange
        when(bookRepository.findById(BookId.of(bookId))).thenReturn(Optional.of(existingBook));
        var command = new UpdateBookCommand(bookId, null, null, null, 9999L, "CNY", null);

        // Act
        UpdateBookResult result = handler.handle(command);

        // Assert
        assertThat(result.priceCents()).isEqualTo(9999L);
    }

    @Test
    void givenNonExistentBook_whenHandle_thenThrowsBookNotFoundException() {
        // Arrange
        when(bookRepository.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new UpdateBookCommand(bookId, null, null, null, null, null, 5)))
                .isInstanceOf(BookNotFoundException.class);
    }
}
