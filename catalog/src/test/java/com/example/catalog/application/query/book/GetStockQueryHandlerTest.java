package com.example.catalog.application.query.book;

import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.domain.ports.BookPersistence;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStockQueryHandlerTest {

    @Mock BookPersistence bookRepository;

    private GetStockQueryHandler handler;
    private final UUID bookId = UUID.randomUUID();
    private Book existingBook;

    @BeforeEach
    void setUp() {
        handler = new GetStockQueryHandler(bookRepository);
        existingBook = Book.reconstitute(
                BookId.of(bookId),
                new Title("Clean Code"),
                new Author("Robert Martin", "Uncle Bob"),
                new Money(4500, "CNY"),
                new Category(UUID.randomUUID(), "Tech"),
                new StockLevel(10, 0));
    }

    @Test
    void givenExistingBook_whenHandle_thenStockResponseReturned() {
        // Arrange
        when(bookRepository.findById(BookId.of(bookId))).thenReturn(Optional.of(existingBook));

        // Act
        StockResult response = handler.handle(new GetStockQuery(bookId));

        // Assert
        assertThat(response.bookId()).isEqualTo(bookId);
        assertThat(response.availableStock()).isEqualTo(10);
    }

    @Test
    void givenNonExistentBook_whenHandle_thenThrowsBookNotFoundException() {
        // Arrange
        when(bookRepository.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new GetStockQuery(bookId)))
                .isInstanceOf(BookNotFoundException.class);
    }
}
