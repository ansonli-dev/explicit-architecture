package com.example.catalog.domain.ports;

import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.VersionedBook;

import java.util.Optional;

/** Writes of existing books, with the row lock held until transaction completion. */
public interface LockedBookPersistence {
    Optional<VersionedBook> lock(BookId id);
    long saveLocked(Book book);
}
