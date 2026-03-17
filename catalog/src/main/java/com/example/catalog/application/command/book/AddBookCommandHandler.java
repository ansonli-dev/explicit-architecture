package com.example.catalog.application.command.book;

import com.example.catalog.application.port.outbound.BookCache;
import com.example.catalog.application.port.outbound.BookPersistence;
import com.example.catalog.application.query.book.BookDetailResponse;
import com.example.catalog.domain.model.*;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddBookCommandHandler implements CommandHandler<AddBookCommand, BookDetailResponse> {

    private final BookPersistence repository;
    private final BookCache cache;

    @Override
    public BookDetailResponse handle(AddBookCommand cmd) {
        Book book = Book.create(
                new Title(cmd.title()),
                new Author(cmd.authorName(), cmd.authorBiography()),
                cmd.priceCents() > 0 ? Money.of(cmd.priceCents(), cmd.currency()) : null,
                Category.create(cmd.categoryName()),
                cmd.initialStock());
        repository.save(book);

        BookDetailResponse res = new BookDetailResponse(book.getId().value(), book.getTitle().value(),
                book.getAuthor().name(), book.getCategory().getName(),
                book.getPrice() != null ? book.getPrice().cents() : 0,
                book.getPrice() != null ? book.getPrice().currency() : "USD", book.getStockLevel().available());
        cache.put(book.getId().value(), res);
        return res;
    }
}
