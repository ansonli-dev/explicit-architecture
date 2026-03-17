package com.example.catalog.application.command.book;

import com.example.catalog.application.port.outbound.BookCache;
import com.example.catalog.application.port.outbound.BookPersistence;
import com.example.catalog.application.query.book.BookDetailResponse;
import com.example.catalog.application.query.book.BookNotFoundException;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateBookCommandHandler implements CommandHandler<UpdateBookCommand, BookDetailResponse> {

    private final BookPersistence repository;
    private final BookCache cache;

    @Override
    public BookDetailResponse handle(UpdateBookCommand cmd) {
        Book book = repository.findById(BookId.of(cmd.id()))
                .orElseThrow(() -> new BookNotFoundException(cmd.id()));
        if (cmd.restockQuantity() != null && cmd.restockQuantity() > 0) {
            book.restock(cmd.restockQuantity());
        }
        repository.save(book);
        cache.invalidate(cmd.id());

        return new BookDetailResponse(book.getId().value(), book.getTitle().value(),
                book.getAuthor().name(), book.getCategory().getName(),
                book.getPrice() != null ? book.getPrice().cents() : 0,
                book.getPrice() != null ? book.getPrice().currency() : "USD", book.getStockLevel().available());
    }
}
