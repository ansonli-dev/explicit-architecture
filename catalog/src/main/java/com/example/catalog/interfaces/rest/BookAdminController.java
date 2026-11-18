package com.example.catalog.interfaces.rest;

import com.example.catalog.application.CatalogDataException;
import com.example.catalog.application.command.book.UpdateBookTitleCommand;
import com.example.catalog.application.query.book.GetBookQuery;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Profile("demo-integrated")
@RestController
@RequestMapping("/api/v1/admin/books")
@RequiredArgsConstructor
class BookAdminController {

    private final QueryBus queries;
    private final CommandBus commands;
    private final DemoCatalogProperties settings;

    @GetMapping("/{bookId}")
    AdminBookResponse adminBook(@PathVariable UUID bookId) {
        var book = queries.dispatch(new GetBookQuery(bookId));
        return new AdminBookResponse(book.id(), book.title(), book.author(), book.category(),
                book.priceMinor(), book.currency(), book.stock(), book.version());
    }

    @PatchMapping("/{bookId}")
    AdminBookResponse changeTitle(@PathVariable UUID bookId, @Valid @RequestBody ChangeTitleRequest request) {
        if (!settings.adminWritesEnabled()) {
            throw new CatalogErrors.AdminWritesClosed();
        }
        var book = commands.dispatch(new UpdateBookTitleCommand(bookId, request.title(), request.expectedVersion()));
        return new AdminBookResponse(book.id(), book.title(), book.author(), book.category(),
                book.priceMinor(), book.currency(), book.stock(), book.version());
    }

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
