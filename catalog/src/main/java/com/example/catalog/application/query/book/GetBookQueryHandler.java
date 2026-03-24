package com.example.catalog.application.query.book;

import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.application.port.outbound.BookCache;
import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.application.port.inbound.GetBookUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetBookQueryHandler implements GetBookUseCase {

    private final BookPersistence repository;
    private final BookCache cache;

    @Override
    public BookDetailResult handle(GetBookQuery query) {
        return cache.get(query.id()).orElseGet(() -> {
            Book book = repository.findById(BookId.of(query.id()))
                    .orElseThrow(() -> new BookNotFoundException(query.id()));
            BookDetailResult result = new BookDetailResult(book.getId().value(), book.getTitle().value(),
                    book.getAuthor().name(), book.getCategory().getName(),
                    book.getPrice() != null ? book.getPrice().cents() : 0,
                    book.getPrice() != null ? book.getPrice().currency() : "USD", book.getStockLevel().available());
            cache.put(query.id(), result);
            return result;
        });
    }
}
