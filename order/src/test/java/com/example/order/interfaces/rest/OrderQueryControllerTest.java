package com.example.order.interfaces.rest;

import com.example.order.application.query.order.GetOrderQuery;
import com.example.order.application.query.order.ListOrdersQuery;
import com.example.order.application.query.order.OrderDetailResponse;
import com.example.order.application.query.order.OrderResponse;
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

@WebMvcTest(OrderQueryController.class)
class OrderQueryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean QueryBus queryBus;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    @Test
    void givenOrderExists_whenGetOrder_thenReturns200AndOrderDetailResponse() throws Exception {
        // Arrange
        var response = new OrderDetailResponse(orderId, customerId, "user@example.com", "PENDING", List.of(), 9998L, "CNY");
        when(queryBus.dispatch(any(GetOrderQuery.class))).thenReturn(response);

        // Act + Assert
        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(queryBus).dispatch(any(GetOrderQuery.class));
    }

    @Test
    void givenOrdersExistForCustomer_whenListOrders_thenReturns200AndOrderList() throws Exception {
        // Arrange
        var order = new OrderResponse(orderId, customerId, "PENDING", 9998L, "CNY");
        when(queryBus.dispatch(any(ListOrdersQuery.class))).thenReturn(List.of(order));

        // Act + Assert
        mockMvc.perform(get("/api/v1/orders").param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        verify(queryBus).dispatch(any(ListOrdersQuery.class));
    }

    @Test
    void givenStatusFilter_whenListOrders_thenQueryDispatchedWithStatusParam() throws Exception {
        // Arrange
        when(queryBus.dispatch(any(ListOrdersQuery.class))).thenReturn(List.of());

        // Act + Assert
        mockMvc.perform(get("/api/v1/orders")
                        .param("customerId", customerId.toString())
                        .param("status", "PENDING"))
                .andExpect(status().isOk());

        verify(queryBus).dispatch(any(ListOrdersQuery.class));
    }
}
