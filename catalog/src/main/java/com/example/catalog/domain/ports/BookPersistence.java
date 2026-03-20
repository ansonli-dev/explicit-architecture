package com.example.catalog.domain.ports;

import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Category;

import java.util.List;
import java.util.Optional;

/** Write-side repository port — domain concept: "the collection of all Books". */
public interface BookPersistence {
    Book save(Book book);

    Optional<Book> findById(BookId id);

    List<Book> findAll(int page, int size, String category);

    boolean existsById(BookId id);

    Optional<Category> findCategoryByName(String name);

    Category saveCategory(Category category);
}
