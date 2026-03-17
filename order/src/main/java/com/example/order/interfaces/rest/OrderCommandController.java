package com.example.order.interfaces.rest;

import com.example.seedwork.application.bus.CommandBus;
import com.example.order.application.command.order.CancelOrderCommand;
import com.example.order.application.command.order.PlaceOrderCommand;
import com.example.order.application.query.order.OrderDetailResponse;
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
    OrderDetailResponse place(@RequestBody PlaceOrderRequest request) {
        List<PlaceOrderCommand.OrderItemRequest> items = request.items().stream()
                .map(i -> new PlaceOrderCommand.OrderItemRequest(
                        i.bookId(), i.bookTitle(), i.unitPriceCents(), i.currency(), i.quantity()))
                .toList();
        return commandBus.dispatch(
                new PlaceOrderCommand(request.customerId(), request.customerEmail(), items));
    }

    @PutMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable UUID id, @RequestBody CancelRequest request) {
        commandBus.dispatch(new CancelOrderCommand(id, request.reason()));
    }

    record PlaceOrderRequest(UUID customerId, String customerEmail, List<ItemRequest> items) {
        record ItemRequest(UUID bookId, String bookTitle, long unitPriceCents, String currency, int quantity) {}
    }

    record CancelRequest(String reason) {}
}
