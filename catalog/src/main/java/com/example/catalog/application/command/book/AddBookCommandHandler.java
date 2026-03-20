package com.example.catalog.application.command.book;

import com.example.catalog.application.port.outbound.BookPersistence;
import com.example.catalog.application.query.book.BookDetailResponse;
import com.example.catalog.domain.model.Author;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.Title;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddBookCommandHandler implements CommandHandler<AddBookCommand, BookDetailResponse> {

    private final BookPersistence repository;

    @Override
    public BookDetailResponse handle(AddBookCommand cmd) {
        if (cmd.priceCents() <= 0) {
            throw new IllegalArgumentException("Book price must be positive");
        }
        Book book = Book.create(
                new Title(cmd.title()),
                new Author(cmd.authorName(), cmd.authorBiography()),
                Money.of(cmd.priceCents(), cmd.currency()),
                Category.create(cmd.categoryName()),
                cmd.initialStock());
        repository.save(book);

        return new BookDetailResponse(book.getId().value(), book.getTitle().value(),
                book.getAuthor().name(), book.getCategory().getName(),
                book.getPrice().cents(), book.getPrice().currency(),
                book.getStockLevel().available());
    }
}
