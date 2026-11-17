package com.example.catalog.interfaces.rest.v2;

import com.example.catalog.application.CatalogDataException;
import com.example.catalog.application.command.book.UpdateBookTitleV2Command;
import com.example.catalog.application.query.book.BookV2Result;
import com.example.catalog.application.query.book.GetBookV2Query;
import com.example.catalog.application.query.book.ListBooksV2Query;
import com.example.catalog.application.query.book.ListCategoriesQuery;
import com.example.catalog.interfaces.rest.security.DemoCatalogProperties;
import com.example.seedwork.application.bus.CommandBus;
import com.example.seedwork.application.bus.QueryBus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Profile("demo-integrated")
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
class BookV2Controller {

    private final QueryBus queries;
    private final CommandBus commands;
    private final DemoCatalogProperties settings;

    @GetMapping("/books")
    BooksResponse books(@RequestParam(required = false) String category,
                        @RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "1") @Min(1) @Max(100) int page,
                        @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        var result = queries.dispatch(new ListBooksV2Query(term(category, 80), term(q, 64), page, size));
        return new BooksResponse(result.books().stream().map(BookResponse::from).toList(),
                result.page(), result.hasNext());
    }

    @GetMapping("/books/{bookId}")
    BookResponse book(@PathVariable UUID bookId) {
        return BookResponse.from(queries.dispatch(new GetBookV2Query(bookId)));
    }

    @GetMapping("/categories")
    CategoriesResponse categories() {
        return new CategoriesResponse(queries.dispatch(new ListCategoriesQuery()));
    }

    @GetMapping("/admin/books/{bookId}")
    AdminBookResponse adminBook(@PathVariable UUID bookId) {
        var book = queries.dispatch(new GetBookV2Query(bookId));
        return new AdminBookResponse(book.id(), book.title(), book.author(), book.category(),
                book.priceMinor(), book.currency(), book.stock(), book.version());
    }

    @PatchMapping("/admin/books/{bookId}")
    AdminBookResponse changeTitle(@PathVariable UUID bookId, @Valid @RequestBody ChangeTitleRequest request) {
        if (!settings.adminWritesEnabled()) {
            throw new CatalogV2Errors.AdminWritesClosed();
        }
        var book = commands.dispatch(new UpdateBookTitleV2Command(bookId, request.title(), request.expectedVersion()));
        return new AdminBookResponse(book.id(), book.title(), book.author(), book.category(),
                book.priceMinor(), book.currency(), book.stock(), book.version());
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

    record BookResponse(UUID id, String title, String author, String category, long priceMinor,
                        String currency, int stock) {
        BookResponse {
            CatalogDataException.checkPrice(priceMinor, currency);
        }

        static BookResponse from(BookV2Result book) {
            return new BookResponse(book.id(), book.title(), book.author(), book.category(), book.priceMinor(),
                    book.currency(), book.stock());
        }
    }

    record BooksResponse(List<BookResponse> books, int page, boolean hasNext) {}

    record CategoriesResponse(List<String> categories) {}

    record AdminBookResponse(UUID id, String title, String author, String category, long priceMinor,
                             String currency, int stock, long version) {
        AdminBookResponse {
            CatalogDataException.checkPrice(priceMinor, currency);
            CatalogDataException.checkInteger(version);
        }
    }

    record ChangeTitleRequest(@NotBlank @Size(max = 500) String title,
                              @NotNull @Min(0) @Max(CatalogDataException.MAX_SAFE_INTEGER) Long expectedVersion) {}
}
