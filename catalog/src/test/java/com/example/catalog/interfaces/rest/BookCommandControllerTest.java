package com.example.catalog.interfaces.rest;

import com.example.catalog.application.command.book.AddBookCommand;
import com.example.catalog.application.command.book.AddBookResult;
import com.example.catalog.application.command.book.ReleaseStockCommand;
import com.example.catalog.application.command.book.ReserveStockCommand;
import com.example.catalog.application.command.book.ReserveStockResult;
import com.example.catalog.application.command.book.UpdateBookCommand;
import com.example.catalog.application.command.book.UpdateBookResult;
import com.example.catalog.application.port.inbound.AddBookUseCase;
import com.example.catalog.application.port.inbound.ReleaseStockUseCase;
import com.example.catalog.application.port.inbound.ReserveStockUseCase;
import com.example.catalog.application.port.inbound.UpdateBookUseCase;
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
    @MockBean AddBookUseCase addBook;
    @MockBean UpdateBookUseCase updateBook;
    @MockBean ReserveStockUseCase reserveStock;
    @MockBean ReleaseStockUseCase releaseStock;

    private final UUID bookId = UUID.randomUUID();

    @Test
    void givenValidAddBookRequest_whenPost_thenReturns201AndBookDetailResponse() throws Exception {
        var result = new AddBookResult(bookId, "Clean Code", "Robert Martin", "Programming", 4999L, "CNY", 100);
        when(addBook.handle(any(AddBookCommand.class))).thenReturn(result);

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

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookId.toString()))
                .andExpect(jsonPath("$.title").value("Clean Code"));

        verify(addBook).handle(any(AddBookCommand.class));
    }

    @Test
    void givenValidUpdateBookRequest_whenPut_thenReturns200AndBookDetailResponse() throws Exception {
        var result = new UpdateBookResult(bookId, "Clean Code 2nd Ed", "Robert Martin", "Programming", 5999L, "CNY", 110);
        when(updateBook.handle(any(UpdateBookCommand.class))).thenReturn(result);

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

        mockMvc.perform(put("/api/v1/books/{id}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clean Code 2nd Ed"));

        verify(updateBook).handle(any(UpdateBookCommand.class));
    }

    @Test
    void givenValidReserveStockRequest_whenPost_thenReturns200AndStockResponse() throws Exception {
        var result = new ReserveStockResult(bookId, 95);
        when(reserveStock.handle(any(ReserveStockCommand.class))).thenReturn(result);

        var body = """
                {
                  "orderId": "%s",
                  "quantity": 5
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/books/{id}/stock/reserve", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStock").value(95));

        verify(reserveStock).handle(any(ReserveStockCommand.class));
    }

    @Test
    void givenValidReleaseStockRequest_whenPost_thenReturns204() throws Exception {
        var body = """
                {
                  "orderId": "%s",
                  "quantity": 5
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/books/{id}/stock/release", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(releaseStock).handle(any(ReleaseStockCommand.class));
    }
}
