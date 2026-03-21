package com.example.catalog.interfaces.rest;

import com.example.catalog.application.query.book.*;
import com.example.catalog.interfaces.rest.response.*;
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
    List<BookSummaryResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<BookSummaryView> views = queryBus.dispatch(ListBooksQuery.of(category, page, size));
        return views.stream().map(BookSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    BookDetailResponse get(@PathVariable UUID id) {
        return BookDetailResponse.from(queryBus.dispatch(new GetBookQuery(id)));
    }

    @GetMapping("/{id}/stock")
    StockResponse getStock(@PathVariable UUID id) {
        return StockResponse.from(queryBus.dispatch(new GetStockQuery(id)));
    }
}
