package com.example.catalog.application.query.book;

import com.example.catalog.application.port.outbound.BookCache;
import com.example.catalog.application.port.outbound.BookPersistence;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBookQueryHandlerTest {

    @Mock BookPersistence bookRepository;
    @Mock BookCache bookCache;

    private GetBookQueryHandler handler;
    private final UUID bookId = UUID.randomUUID();
    private Book existingBook;
    private BookDetailResponse cachedResponse;

    @BeforeEach
    void setUp() {
        handler = new GetBookQueryHandler(bookRepository, bookCache);
        existingBook = Book.reconstitute(
                BookId.of(bookId),
                new Title("Clean Code"),
                new Author("Robert Martin", "Uncle Bob"),
                new Money(4500, "CNY"),
                new Category(UUID.randomUUID(), "Tech"),
                new StockLevel(10, 0));
        cachedResponse = new BookDetailResponse(bookId, "Clean Code", "Robert Martin", "Tech", 4500, "CNY", 10);
    }

    @Test
    void givenCacheHit_whenHandle_thenRepositoryNotQueried() {
        // Arrange
        when(bookCache.get(bookId)).thenReturn(Optional.of(cachedResponse));

        // Act
        BookDetailResponse response = handler.handle(new GetBookQuery(bookId));

        // Assert
        assertThat(response.title()).isEqualTo("Clean Code");
        verify(bookRepository, never()).findById(any());
    }

    @Test
    void givenCacheMiss_whenHandle_thenLoadedFromRepositoryAndResultCached() {
        // Arrange
        when(bookCache.get(bookId)).thenReturn(Optional.empty());
        when(bookRepository.findById(BookId.of(bookId))).thenReturn(Optional.of(existingBook));

        // Act
        handler.handle(new GetBookQuery(bookId));

        // Assert
        verify(bookRepository).findById(BookId.of(bookId));
        verify(bookCache).put(any(UUID.class), any(BookDetailResponse.class));
    }

    @Test
    void givenBookNotFound_whenHandle_thenThrowsBookNotFoundException() {
        // Arrange
        when(bookCache.get(any())).thenReturn(Optional.empty());
        when(bookRepository.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> handler.handle(new GetBookQuery(bookId)))
                .isInstanceOf(BookNotFoundException.class);
    }
}
