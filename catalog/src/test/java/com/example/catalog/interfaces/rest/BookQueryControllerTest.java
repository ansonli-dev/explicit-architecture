package com.example.catalog.interfaces.rest;

import com.example.catalog.application.query.book.BookDetailView;
import com.example.catalog.application.query.book.BookSummaryView;
import com.example.catalog.application.query.book.GetBookQuery;
import com.example.catalog.application.query.book.GetStockQuery;
import com.example.catalog.application.query.book.ListBooksQuery;
import com.example.catalog.application.query.book.StockView;
import com.example.seedwork.application.bus.QueryBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookQueryController.class)
class BookQueryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean QueryBus queryBus;

    private final UUID bookId = UUID.randomUUID();

    @Test
    void givenBooksExist_whenListBooks_thenReturns200AndBookList() throws Exception {
        // Arrange
        var book = new BookSummaryView(bookId, "Clean Code", "Robert Martin", "Programming", 4999L, "CNY", 100);
        when(queryBus.dispatch(any(ListBooksQuery.class))).thenReturn(List.of(book));

        // Act + Assert
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(bookId.toString()))
                .andExpect(jsonPath("$[0].title").value("Clean Code"));

        verify(queryBus).dispatch(any(ListBooksQuery.class));
    }

    @Test
    void givenCategoryFilter_whenListBooks_thenQueryDispatchedWithCategory() throws Exception {
        // Arrange
        when(queryBus.dispatch(any(ListBooksQuery.class))).thenReturn(List.of());

        // Act + Assert
        mockMvc.perform(get("/api/v1/books").param("category", "Programming"))
                .andExpect(status().isOk());

        verify(queryBus).dispatch(any(ListBooksQuery.class));
    }

    @Test
    void givenBookExists_whenGetBook_thenReturns200AndBookDetailResponse() throws Exception {
        // Arrange
        var response = new BookDetailView(bookId, "Clean Code", "Robert Martin", "Programming", 4999L, "CNY", 100);
        when(queryBus.dispatch(any(GetBookQuery.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(get("/api/v1/books/{id}", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookId.toString()))
                .andExpect(jsonPath("$.title").value("Clean Code"));

        verify(queryBus).dispatch(any(GetBookQuery.class));
    }

    @Test
    void givenBookExists_whenGetStock_thenReturns200AndStockResponse() throws Exception {
        // Arrange
        var response = new StockView(bookId, 95);
        when(queryBus.dispatch(any(GetStockQuery.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(get("/api/v1/books/{id}/stock", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId.toString()))
                .andExpect(jsonPath("$.availableStock").value(95));

        verify(queryBus).dispatch(any(GetStockQuery.class));
    }
}
