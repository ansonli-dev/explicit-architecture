package com.example.catalog.application.port.outbound;
import com.example.catalog.application.query.book.BookDetailResult;
import java.util.Optional;
import java.util.UUID;
public interface BookCache {
    Optional<BookDetailResult> get(UUID id);
    void put(UUID id, BookDetailResult book);
    void invalidate(UUID id);
}
