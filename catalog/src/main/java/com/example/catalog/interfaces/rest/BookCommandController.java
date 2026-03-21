package com.example.catalog.interfaces.rest;

import com.example.catalog.application.command.book.AddBookCommand;
import com.example.catalog.application.command.book.ReleaseStockCommand;
import com.example.catalog.application.command.book.ReserveStockCommand;
import com.example.catalog.application.command.book.UpdateBookCommand;
import com.example.catalog.application.port.inbound.AddBookUseCase;
import com.example.catalog.application.port.inbound.ReleaseStockUseCase;
import com.example.catalog.application.port.inbound.ReserveStockUseCase;
import com.example.catalog.application.port.inbound.UpdateBookUseCase;
import com.example.catalog.interfaces.rest.request.AddBookRequest;
import com.example.catalog.interfaces.rest.request.ReleaseStockRequest;
import com.example.catalog.interfaces.rest.request.ReserveStockRequest;
import com.example.catalog.interfaces.rest.request.UpdateBookRequest;
import com.example.catalog.interfaces.rest.response.AddBookResponse;
import com.example.catalog.interfaces.rest.response.ReserveStockResponse;
import com.example.catalog.interfaces.rest.response.UpdateBookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
class BookCommandController {

    private final AddBookUseCase addBook;
    private final UpdateBookUseCase updateBook;
    private final ReserveStockUseCase reserveStock;
    private final ReleaseStockUseCase releaseStock;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AddBookResponse add(@RequestBody AddBookRequest request) {
        return AddBookResponse.from(addBook.handle(new AddBookCommand(
                request.title(), request.authorName(), request.authorBiography(),
                request.priceCents(), request.currency(), request.categoryName(), request.initialStock())));
    }

    @PutMapping("/{id}")
    UpdateBookResponse update(@PathVariable UUID id, @RequestBody UpdateBookRequest request) {
        return UpdateBookResponse.from(updateBook.handle(new UpdateBookCommand(
                id, request.title(), request.authorName(), request.authorBiography(),
                request.priceCents(), request.currency(), request.restockQuantity())));
    }

    @PostMapping("/{id}/stock/reserve")
    ReserveStockResponse reserve(@PathVariable UUID id, @RequestBody ReserveStockRequest request) {
        return ReserveStockResponse.from(reserveStock.handle(new ReserveStockCommand(id, request.orderId(), request.quantity())));
    }

    @PostMapping("/{id}/stock/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void release(@PathVariable UUID id, @RequestBody ReleaseStockRequest request) {
        releaseStock.handle(new ReleaseStockCommand(id, request.orderId(), request.quantity()));
    }
}
