package com.example.order.interfaces.rest;

import com.example.order.application.command.order.CancelOrderCommand;
import com.example.order.application.command.order.PlaceOrderCommand;
import com.example.order.application.command.order.PlaceOrderResult;
import com.example.order.interfaces.dto.CancelOrderRequest;
import com.example.order.interfaces.dto.PlaceOrderRequest;
import com.example.seedwork.application.bus.CommandBus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
class OrderCommandController {

    private final CommandBus commandBus;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PlaceOrderResult place(@Valid @RequestBody PlaceOrderRequest request) {
        List<PlaceOrderCommand.OrderItem> commandItems = request.items().stream()
                .map(i -> new PlaceOrderCommand.OrderItem(
                        i.bookId(), i.bookTitle(), i.unitPriceCents(), i.currency(), i.quantity()))
                .toList();
        return commandBus.dispatch(new PlaceOrderCommand(
                request.customerId(), request.customerEmail(), commandItems));
    }

    @PutMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable UUID id, @RequestBody CancelOrderRequest request) {
        commandBus.dispatch(new CancelOrderCommand(id, request.reason()));
    }
}
