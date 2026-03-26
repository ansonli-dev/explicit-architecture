package com.example.order.interfaces.event;

import com.example.order.application.command.order.AutoConfirmOrderCommand;
import com.example.order.domain.event.OrderPlaced;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;
import com.example.seedwork.application.bus.CommandBus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderPlacedAutoConfirmListenerTest {

    @Mock CommandBus commandBus;
    @InjectMocks OrderPlacedAutoConfirmListener listener;

    @Test
    void onOrderPlaced_dispatchesAutoConfirmCommand() {
        var orderId = OrderId.of(UUID.randomUUID());
        var event = new OrderPlaced(UUID.randomUUID(), orderId, CustomerId.of(UUID.randomUUID()),
                "a@b.com", List.of(), Money.of(3000, "CNY"), Instant.now());

        listener.onOrderPlaced(event);

        var captor = ArgumentCaptor.forClass(AutoConfirmOrderCommand.class);
        verify(commandBus).dispatch(captor.capture());

        AutoConfirmOrderCommand dispatched = captor.getValue();
        assertThat(dispatched.orderId()).isEqualTo(orderId.value());
        assertThat(dispatched.thresholdCents()).isEqualTo(5000L);
        assertThat(dispatched.thresholdCurrency()).isEqualTo("CNY");
    }
}
