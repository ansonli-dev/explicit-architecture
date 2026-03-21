package com.example.catalog.interfaces.rest;

import com.example.catalog.application.command.book.*;
import com.example.catalog.application.query.book.StockResponse;
import com.example.catalog.interfaces.dto.*;
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
    AddBookResult add(@RequestBody AddBookRequest request) {
        return commandBus.dispatch(new AddBookCommand(
                request.title(), request.authorName(), request.authorBiography(),
                request.priceCents(), request.currency(), request.categoryName(), request.initialStock()));
    }

    @PutMapping("/{id}")
    UpdateBookResult update(@PathVariable UUID id, @RequestBody UpdateBookRequest request) {
        return commandBus.dispatch(new UpdateBookCommand(
                id, request.title(), request.authorName(), request.authorBiography(),
                request.priceCents(), request.currency(), request.restockQuantity()));
    }

    @PostMapping("/{id}/stock/reserve")
    StockResponse reserve(@PathVariable UUID id, @RequestBody ReserveStockRequest request) {
        return commandBus.dispatch(new ReserveStockCommand(id, request.orderId(), request.quantity()));
    }

    @PostMapping("/{id}/stock/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable UUID id, @RequestBody ReleaseStockRequest request) {
        commandBus.dispatch(new ReleaseStockCommand(id, request.orderId(), request.quantity()));
    }

}
