package com.example.catalog.interfaces.rest;

import com.example.catalog.application.port.inbound.GetStockUseCase;
import com.example.catalog.application.query.book.GetStockQuery;
import com.example.catalog.application.query.book.StockResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockQueryController.class)
class StockQueryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean GetStockUseCase getStock;

    private final UUID bookId = UUID.randomUUID();

    @Test
    void givenBookExists_whenGetStock_thenReturns200AndStockResponse() throws Exception {
        var result = new StockResult(bookId, 95);
        when(getStock.handle(any(GetStockQuery.class))).thenReturn(result);

        mockMvc.perform(get("/api/v1/books/{id}/stock", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(bookId.toString()))
                .andExpect(jsonPath("$.availableStock").value(95));

        verify(getStock).handle(any(GetStockQuery.class));
    }
}
