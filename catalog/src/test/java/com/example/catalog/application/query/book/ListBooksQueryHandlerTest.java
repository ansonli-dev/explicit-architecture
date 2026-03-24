package com.example.catalog.application.query.book;

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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListBooksQueryHandlerTest {

    @Mock BookPersistence bookRepository;

    private ListBooksQueryHandler handler;
    private Book existingBook;

    @BeforeEach
    void setUp() {
        handler = new ListBooksQueryHandler(bookRepository);
        existingBook = Book.reconstitute(
                BookId.of(UUID.randomUUID()),
                new Title("Clean Code"),
                new Author("Robert Martin", "Uncle Bob"),
                new Money(4500, "CNY"),
                new Category(UUID.randomUUID(), "Tech"),
                new StockLevel(10, 0));
    }

    @Test
    void givenBooksExist_whenHandle_thenAllMappedAndReturned() {
        // Arrange
        when(bookRepository.findAll(0, 20, null)).thenReturn(List.of(existingBook));

        // Act
        List<BookSummaryResult> responses = handler.handle(new ListBooksQuery(null, 0, 20));

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).title()).isEqualTo("Clean Code");
    }

    @Test
    void givenNoBooks_whenHandle_thenEmptyListReturned() {
        // Arrange
        when(bookRepository.findAll(0, 20, null)).thenReturn(List.of());

        // Act
        List<BookSummaryResult> responses = handler.handle(new ListBooksQuery(null, 0, 20));

        // Assert
        assertThat(responses).isEmpty();
    }

    @Test
    void givenCategoryFilter_whenHandle_thenFilterPassedToRepository() {
        // Arrange
        when(bookRepository.findAll(0, 20, "Tech")).thenReturn(List.of(existingBook));

        // Act
        List<BookSummaryResult> responses = handler.handle(new ListBooksQuery("Tech", 0, 20));

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).category()).isEqualTo("Tech");
    }
}
