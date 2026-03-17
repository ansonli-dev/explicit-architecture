package com.example.catalog.application.port.outbound;

import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Category;

import java.util.Optional;
import java.util.List;

public interface BookPersistence {
    Book save(Book book);

    Optional<Book> findById(BookId id);

    List<Book> findAll(int page, int size, String category);

    boolean existsById(BookId id);

    Optional<Category> findCategoryByName(String name);

    Category saveCategory(Category category);
}
