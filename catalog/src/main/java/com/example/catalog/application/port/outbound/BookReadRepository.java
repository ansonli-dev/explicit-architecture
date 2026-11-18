package com.example.catalog.application.port.outbound;

import com.example.catalog.application.query.book.BookReadResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Book projection with current availability from PostgreSQL. */
public interface BookReadRepository {
    List<BookReadResult> search(String category, String query, int offset, int limit);
    Optional<BookReadResult> find(UUID id);
    List<BookReadResult> findAll(Collection<UUID> ids);
    List<String> categories();
}
