package com.example.catalog.interfaces.rest;

import com.example.catalog.application.CatalogDataException;
import com.example.catalog.application.query.book.BookReadResult;
import com.example.catalog.application.query.book.GetBookQuery;
import com.example.catalog.application.query.book.ListBooksQuery;
import com.example.catalog.application.query.book.ListCategoriesQuery;
import com.example.seedwork.application.bus.QueryBus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class BookQueryController {

    private final QueryBus queries;

    @GetMapping("/books")
    BooksResponse books(@RequestParam(required = false) String category,
                        @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "1") @Min(1) @Max(100) int page,
                        @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        var result = queries.dispatch(new ListBooksQuery(term(category, 80), term(q, 64), page, size));
        return new BooksResponse(result.books().stream().map(book -> BookResponse.from(book, false)).toList(),
                result.page(), result.hasNext());
    }

    @GetMapping("/books/{bookId}")
    BookResponse book(@PathVariable UUID bookId) {
        return BookResponse.from(queries.dispatch(new GetBookQuery(bookId)), true);
    }

    @GetMapping("/categories")
    CategoriesResponse categories() {
        return new CategoriesResponse(queries.dispatch(new ListCategoriesQuery()));
    }

    private static String term(String value, int max) {
        if (value == null) {
            return null;
        }
        String term = value.strip();
        if (term.isEmpty() || term.length() > max) {
            throw new IllegalArgumentException("Invalid search parameter");
        }
        return term;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BookResponse(UUID id, String title, String author, String category, long priceMinor,
                        String currency, int stock, String coverUrl, String description) {
        BookResponse {
            CatalogDataException.checkPrice(priceMinor, currency);
        }

        static BookResponse from(BookReadResult book, boolean includeDescription) {
            return new BookResponse(book.id(), book.title(), book.author(), book.category(), book.priceMinor(),
                    book.currency(), book.stock(), book.coverUrl(), includeDescription ? book.description() : null);
        }
    }

    record BooksResponse(List<BookResponse> books, int page, boolean hasNext) {}

    record CategoriesResponse(List<String> categories) {}
}
