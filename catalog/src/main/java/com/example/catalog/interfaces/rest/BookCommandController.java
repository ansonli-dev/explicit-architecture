package com.example.catalog.interfaces.rest;

import com.example.catalog.application.command.book.*;
import com.example.catalog.interfaces.rest.request.*;
import com.example.catalog.interfaces.rest.response.*;
import com.example.seedwork.application.bus.CommandBus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
class BookCommandController {

    private final CommandBus commandBus;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AddBookResponse add(@RequestBody AddBookRequest request) {
        return AddBookResponse.from(commandBus.dispatch(new AddBookCommand(
                request.title(), request.authorName(), request.authorBiography(),
                request.priceCents(), request.currency(), request.categoryName(), request.initialStock())));
    }

    @PutMapping("/{id}")
    UpdateBookResponse update(@PathVariable UUID id, @RequestBody UpdateBookRequest request) {
        return UpdateBookResponse.from(commandBus.dispatch(new UpdateBookCommand(
                id, request.title(), request.authorName(), request.authorBiography(),
                request.priceCents(), request.currency(), request.restockQuantity())));
    }

    @PostMapping("/{id}/stock/reserve")
    ReserveStockResponse reserve(@PathVariable UUID id, @RequestBody ReserveStockRequest request) {
        return ReserveStockResponse.from(commandBus.dispatch(new ReserveStockCommand(id, request.orderId(), request.quantity())));
    }

    @PostMapping("/{id}/stock/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable UUID id, @RequestBody ReleaseStockRequest request) {
        commandBus.dispatch(new ReleaseStockCommand(id, request.orderId(), request.quantity()));
    }

}
