package com.example.catalog.application.command.book;

import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.domain.model.Author;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.Title;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateBookCommandHandler implements CommandHandler<UpdateBookCommand, UpdateBookResult> {

    private final BookPersistence repository;

    @Override
    public UpdateBookResult handle(UpdateBookCommand cmd) {
        Book book = repository.findById(BookId.of(cmd.id()))
                .orElseThrow(() -> new BookNotFoundException(cmd.id()));

        // Apply metadata update if any field provided
        Title newTitle = cmd.title() != null ? new Title(cmd.title()) : null;
        Author newAuthor = cmd.authorName() != null
                ? new Author(cmd.authorName(), cmd.authorBiography())
                : null;
        if (newTitle != null || newAuthor != null) {
            book.updateMetadata(newTitle, newAuthor);
        }

        // Money constructor enforces positive price; command constructor enforces fields-together.
        if (cmd.priceCents() != null) {
            book.updatePrice(Money.of(cmd.priceCents(), cmd.currency()));
        }

        // Apply restock if provided
        if (cmd.restockQuantity() != null && cmd.restockQuantity() > 0) {
            book.restock(cmd.restockQuantity());
        }

        repository.save(book);

        return new UpdateBookResult(book.getId().value(), book.getTitle().value(),
                book.getAuthor().name(), book.getCategory().getName(),
                book.getPrice().cents(), book.getPrice().currency(),
                book.getStockLevel().available());
    }
}
