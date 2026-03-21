package com.example.catalog.application.port.outbound;
import com.example.catalog.application.query.book.BookDetailView;
import java.util.Optional;
import java.util.UUID;
public interface BookCache {
    Optional<BookDetailView> get(UUID id);
    void put(UUID id, BookDetailView book);
    void invalidate(UUID id);
}
