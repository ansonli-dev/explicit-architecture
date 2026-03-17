package com.example.catalog.interfaces.rest;

import com.example.catalog.application.query.book.*;
import com.example.seedwork.application.bus.QueryBus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
class BookQueryController {

    private final QueryBus queryBus;

    @GetMapping
    List<BookResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queryBus.dispatch(ListBooksQuery.of(category, page, size));
    }

    @GetMapping("/{id}")
    BookDetailResponse get(@PathVariable UUID id) {
        return queryBus.dispatch(new GetBookQuery(id));
    }

    @GetMapping("/{id}/stock")
    StockResponse getStock(@PathVariable UUID id) {
        return queryBus.dispatch(new GetStockQuery(id));
    }
}
