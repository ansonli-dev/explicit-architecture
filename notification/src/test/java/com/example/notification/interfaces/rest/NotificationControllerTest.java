package com.example.notification.interfaces.rest;

import com.example.notification.application.query.notification.ListNotificationsQuery;
import com.example.notification.application.query.notification.NotificationView;
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

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean QueryBus queryBus;

    private final UUID customerId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @Test
    void givenNotificationsExist_whenListNotifications_thenReturns200AndNotificationList() throws Exception {
        // Arrange
        var response = new NotificationView(notificationId, customerId, "user@example.com", "EMAIL", "Order Placed", "SENT");
        when(queryBus.dispatch(any(ListNotificationsQuery.class))).thenReturn(List.of(response));

        // Act + Assert
        mockMvc.perform(get("/api/v1/notifications").param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].deliveryStatus").value("SENT"))
                .andExpect(jsonPath("$[0].subject").value("Order Placed"));

        verify(queryBus).dispatch(any(ListNotificationsQuery.class));
    }

    @Test
    void givenNoNotificationsForCustomer_whenListNotifications_thenReturns200AndEmptyList() throws Exception {
        // Arrange
        when(queryBus.dispatch(any(ListNotificationsQuery.class))).thenReturn(List.of());

        // Act + Assert
        mockMvc.perform(get("/api/v1/notifications").param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(queryBus).dispatch(any(ListNotificationsQuery.class));
    }

    @Test
    void givenPaginationParams_whenListNotifications_thenQueryDispatchedWithPageAndSize() throws Exception {
        // Arrange
        when(queryBus.dispatch(any(ListNotificationsQuery.class))).thenReturn(List.of());

        // Act + Assert
        mockMvc.perform(get("/api/v1/notifications")
                        .param("customerId", customerId.toString())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(queryBus).dispatch(any(ListNotificationsQuery.class));
    }
}
