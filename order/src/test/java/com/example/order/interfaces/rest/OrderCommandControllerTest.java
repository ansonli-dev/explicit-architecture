package com.example.order.interfaces.rest;

import com.example.order.application.command.order.CancelOrderCommand;
import com.example.order.application.command.order.PlaceOrderCommand;
import com.example.order.application.query.order.OrderDetailResponse;
import com.example.seedwork.application.bus.CommandBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderCommandController.class)
class OrderCommandControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean CommandBus commandBus;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID bookId = UUID.randomUUID();

    @Test
    void givenValidPlaceOrderRequest_whenPost_thenReturns201AndOrderDetailResponse() throws Exception {
        // Arrange
        var response = new OrderDetailResponse(orderId, customerId, "user@example.com", "PENDING", List.of(), 9998L, "CNY");
        when(commandBus.dispatch(any(PlaceOrderCommand.class))).thenReturn(response);

        var body = """
                {
                  "customerId": "%s",
                  "customerEmail": "user@example.com",
                  "items": [
                    {
                      "bookId": "%s",
                      "bookTitle": "Clean Code",
                      "unitPriceCents": 4999,
                      "currency": "CNY",
                      "quantity": 2
                    }
                  ]
                }
                """.formatted(customerId, bookId);

        // Act + Assert
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(commandBus).dispatch(any(PlaceOrderCommand.class));
    }

    @Test
    void givenValidCancelRequest_whenPut_thenReturns204() throws Exception {
        // Arrange
        when(commandBus.dispatch(any(CancelOrderCommand.class))).thenReturn(null);

        var body = """
                {
                  "reason": "Customer changed mind"
                }
                """;

        // Act + Assert
        mockMvc.perform(put("/api/v1/orders/{id}/cancel", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        verify(commandBus).dispatch(any(CancelOrderCommand.class));
    }
}
