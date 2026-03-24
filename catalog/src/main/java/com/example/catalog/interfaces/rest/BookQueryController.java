package com.example.catalog.interfaces.rest;

import com.example.catalog.application.port.inbound.GetBookUseCase;
import com.example.catalog.application.port.inbound.GetStockUseCase;
import com.example.catalog.application.port.inbound.ListBooksUseCase;
import com.example.catalog.application.query.book.GetBookQuery;
import com.example.catalog.application.query.book.GetStockQuery;
import com.example.catalog.application.query.book.BookSummaryResult;
import com.example.catalog.application.query.book.ListBooksQuery;
import com.example.catalog.interfaces.rest.response.BookDetailResponse;
import com.example.catalog.interfaces.rest.response.BookSummaryResponse;
import com.example.catalog.interfaces.rest.response.StockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
class BookQueryController {

    private final ListBooksUseCase listBooks;
    private final GetBookUseCase getBook;
    private final GetStockUseCase getStock;

    @GetMapping
    List<BookSummaryResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<BookSummaryResult> views = listBooks.handle(ListBooksQuery.of(category, page, size));
        return views.stream().map(BookSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    BookDetailResponse get(@PathVariable UUID id) {
        return BookDetailResponse.from(getBook.handle(new GetBookQuery(id)));
    }

    @GetMapping("/{id}/stock")
    StockResponse stock(@PathVariable UUID id) {
        return StockResponse.from(getStock.handle(new GetStockQuery(id)));
    }
}
