package com.example.order.application.query.order;

import com.example.order.application.port.outbound.OrderSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListOrdersQueryHandlerTest {

    @Mock OrderSearchRepository orderSearchRepository;

    private ListOrdersQueryHandler handler;
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new ListOrdersQueryHandler(orderSearchRepository);
    }

    @Test
    void givenOrdersExistForCustomer_whenHandle_thenAllReturned() {
        // Arrange
        var orderResponse = new OrderSummaryResult(orderId, customerId, "PENDING", 20_000, "CNY");
        when(orderSearchRepository.findByCustomerIdAndStatus(customerId, null, 0, 20))
                .thenReturn(List.of(orderResponse));

        // Act
        List<OrderSummaryResult> responses = handler.handle(new ListOrdersQuery(customerId, null, 0, 20));

        // Assert
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).orderId()).isEqualTo(orderId);
    }

    @Test
    void givenNoOrdersForCustomer_whenHandle_thenEmptyListReturned() {
        // Arrange
        when(orderSearchRepository.findByCustomerIdAndStatus(any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        // Act
        List<OrderSummaryResult> responses = handler.handle(new ListOrdersQuery(customerId, "PENDING", 0, 20));

        // Assert
        assertThat(responses).isEmpty();
    }
}
