package com.example.catalog.application.port.outbound;

import com.example.catalog.application.query.book.BookV2Result;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Authoritative v2 book projection; does not consult the v1 cache. */
public interface BookReadRepository {
    List<BookV2Result> search(String category, String query, int offset, int limit);
    Optional<BookV2Result> find(UUID id);
    List<BookV2Result> findAll(Collection<UUID> ids);
    List<String> categories();
}
