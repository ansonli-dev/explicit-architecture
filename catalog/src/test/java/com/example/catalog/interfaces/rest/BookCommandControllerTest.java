package com.example.catalog.interfaces.rest;

import com.example.catalog.application.command.book.AddBookCommand;
import com.example.catalog.application.command.book.AddBookResult;
import com.example.catalog.application.command.book.ReleaseStockCommand;
import com.example.catalog.application.command.book.ReserveStockCommand;
import com.example.catalog.application.command.book.UpdateBookCommand;
import com.example.catalog.application.command.book.UpdateBookResult;
import com.example.catalog.application.query.book.StockResponse;
import com.example.seedwork.application.bus.CommandBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookCommandController.class)
class BookCommandControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CommandBus commandBus;

    private final UUID bookId = UUID.randomUUID();

    @Test
    void givenValidAddBookRequest_whenPost_thenReturns201AndBookDetailResponse() throws Exception {
        // Arrange
        var response = new AddBookResult(bookId, "Clean Code", "Robert Martin", "Programming", 4999L, "CNY", 100);
        when(commandBus.dispatch(any(AddBookCommand.class))).thenReturn(response);

        var body = """
                {
                  "title": "Clean Code",
                  "authorName": "Robert Martin",
                  "authorBiography": "Software engineer",
                  "priceCents": 4999,
                  "currency": "CNY",
                  "categoryName": "Programming",
                  "initialStock": 100
                }
                """;

        // Act + Assert
        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookId.toString()))
                .andExpect(jsonPath("$.title").value("Clean Code"));

        verify(commandBus).dispatch(any(AddBookCommand.class));
    }

    @Test
    void givenValidUpdateBookRequest_whenPut_thenReturns200AndBookDetailResponse() throws Exception {
        // Arrange
        var response = new UpdateBookResult(bookId, "Clean Code 2nd Ed", "Robert Martin", "Programming", 5999L, "CNY", 110);
        when(commandBus.dispatch(any(UpdateBookCommand.class))).thenReturn(response);

        var body = """
                {
                  "title": "Clean Code 2nd Ed",
                  "authorName": "Robert Martin",
                  "authorBiography": "Software engineer",
                  "priceCents": 5999,
                  "currency": "CNY",
                  "restockQuantity": 10
                }
                """;

        // Act + Assert
        mockMvc.perform(put("/api/v1/books/{id}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code 2nd Ed"));

        verify(commandBus).dispatch(any(UpdateBookCommand.class));
    }

    @Test
    void givenValidReserveStockRequest_whenPost_thenReturns200AndStockResponse() throws Exception {
        // Arrange
        var response = new StockResponse(bookId, 95);
        when(commandBus.dispatch(any(ReserveStockCommand.class))).thenReturn(response);

        var body = """
                {
                  "orderId": "%s",
                  "quantity": 5
                }
                """.formatted(UUID.randomUUID());

        // Act + Assert
        mockMvc.perform(post("/api/v1/books/{id}/stock/reserve", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStock").value(95));

        verify(commandBus).dispatch(any(ReserveStockCommand.class));
    }

    @Test
    void givenValidReleaseStockRequest_whenPost_thenReturns204() throws Exception {
        // Arrange
        when(commandBus.dispatch(any(ReleaseStockCommand.class))).thenReturn(null);

        var body = """
                {
                  "orderId": "%s",
                  "quantity": 5
                }
                """.formatted(UUID.randomUUID());

        // Act + Assert
        mockMvc.perform(post("/api/v1/books/{id}/stock/release", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(commandBus).dispatch(any(ReleaseStockCommand.class));
    }
}
