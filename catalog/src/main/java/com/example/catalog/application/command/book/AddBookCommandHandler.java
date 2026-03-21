package com.example.catalog.application.command.book;

import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.domain.model.Author;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.Title;
import com.example.catalog.application.port.inbound.AddBookUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddBookCommandHandler implements AddBookUseCase {

    private final BookPersistence repository;

    @Override
    public AddBookResult handle(AddBookCommand cmd) {
        Book book = Book.create(
                new Title(cmd.title()),
                new Author(cmd.authorName(), cmd.authorBiography()),
                Money.of(cmd.priceCents(), cmd.currency()),
                Category.create(cmd.categoryName()),
                cmd.initialStock());
        repository.save(book);

        return new AddBookResult(book.getId().value(), book.getTitle().value(),
                book.getAuthor().name(), book.getCategory().getName(),
                book.getPrice().cents(), book.getPrice().currency(),
                book.getStockLevel().available());
    }
}
