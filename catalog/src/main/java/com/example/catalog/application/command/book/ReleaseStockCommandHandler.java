package com.example.catalog.application.command.book;

import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReleaseStockCommandHandler implements CommandHandler<ReleaseStockCommand, Void> {

    private final BookPersistence repository;

    @Override
    public Void handle(ReleaseStockCommand cmd) {
        Book book = repository.findById(BookId.of(cmd.bookId()))
                .orElseThrow(() -> new BookNotFoundException(cmd.bookId()));
        book.release(cmd.orderId(), cmd.quantity());
        repository.save(book);
        return null;
    }
}
