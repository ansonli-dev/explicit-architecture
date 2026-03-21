package com.example.catalog.application.command.book;

import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.application.port.inbound.ReleaseStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReleaseStockCommandHandler implements ReleaseStockUseCase {

    private final BookPersistence repository;

    @Override
    public void handle(ReleaseStockCommand cmd) {
        Book book = repository.findById(BookId.of(cmd.bookId()))
                .orElseThrow(() -> new BookNotFoundException(cmd.bookId()));
        book.release(cmd.orderId(), cmd.quantity());
        repository.save(book);
    }
}
