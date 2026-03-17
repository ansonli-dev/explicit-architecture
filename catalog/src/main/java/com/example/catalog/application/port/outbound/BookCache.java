package com.example.catalog.application.port.outbound;
import com.example.catalog.application.query.book.BookDetailResponse;
import com.example.catalog.domain.model.Book;
import java.util.Optional;
import java.util.UUID;
public interface BookCache {
    Optional<BookDetailResponse> get(UUID id);
    void put(UUID id, BookDetailResponse book);
    void invalidate(UUID id);
}
