package com.example.order.application.query.order;

import com.example.order.application.port.outbound.OrderReadRepository;
import com.example.order.application.port.outbound.OrderSearchRepository;
import com.example.order.domain.model.OrderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrderQueryHandlerTest {

    @Mock OrderSearchRepository orderSearchRepository;
    @Mock OrderReadRepository orderReadRepository;

    private GetOrderQueryHandler handler;
    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private OrderDetailResponse esResponse;
    private OrderDetailResponse dbResponse;

    @BeforeEach
    void setUp() {
        handler = new GetOrderQueryHandler(orderSearchRepository, orderReadRepository);
        esResponse = new OrderDetailResponse(orderId, customerId, "user@example.com",
                "PENDING", List.of(), 20_000, "CNY");
        dbResponse = new OrderDetailResponse(orderId, customerId, "user@example.com",
                "PENDING", List.of(), 20_000, "CNY");
    }

    @Test
    void givenOrderInElasticSearch_whenHandle_thenReturnedFromEsWithoutFallingBack() {
        when(orderSearchRepository.findById(orderId)).thenReturn(Optional.of(esResponse));

        OrderDetailResponse response = handler.handle(new GetOrderQuery(orderId));

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(orderReadRepository, never()).findDetailById(any());
    }

    @Test
    void givenEsMissAndOrderInPostgres_whenHandle_thenFallenBackToPostgres() {
        when(orderSearchRepository.findById(orderId)).thenReturn(Optional.empty());
        when(orderReadRepository.findDetailById(OrderId.of(orderId))).thenReturn(Optional.of(dbResponse));

        OrderDetailResponse response = handler.handle(new GetOrderQuery(orderId));

        assertThat(response.orderId()).isEqualTo(orderId);
        verify(orderReadRepository).findDetailById(OrderId.of(orderId));
    }

    @Test
    void givenOrderNotFoundInEitherStore_whenHandle_thenThrowsOrderNotFoundException() {
        when(orderSearchRepository.findById(orderId)).thenReturn(Optional.empty());
        when(orderReadRepository.findDetailById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetOrderQuery(orderId)))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
