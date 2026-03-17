package com.example.catalog.infrastructure.repository.jpa;

import com.example.catalog.domain.model.Author;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.StockLevel;
import com.example.catalog.domain.model.Title;

class BookMapper {

    private BookMapper() {
    }

    static Book toDomain(BookJpaEntity entity) {
        Category category = new Category(entity.getCategory().getId(), entity.getCategory().getName());
        return Book.reconstitute(
                BookId.of(entity.getId()),
                new Title(entity.getTitle()),
                new Author(entity.getAuthorName(), entity.getAuthorBiography()),
                new Money(entity.getPriceCents(), entity.getCurrency()),
                category,
                new StockLevel(entity.getStockTotal(), entity.getStockReserved()));
    }

    static BookJpaEntity toEntity(Book book, CategoryJpaEntity categoryJpaEntity) {
        BookJpaEntity entity = new BookJpaEntity();
        entity.setId(book.getId().value());
        entity.setTitle(book.getTitle().value());
        entity.setAuthorName(book.getAuthor().name());
        entity.setAuthorBiography(book.getAuthor().biography());
        entity.setPriceCents(book.getPrice().cents());
        entity.setCurrency(book.getPrice().currency());
        entity.setCategory(categoryJpaEntity);
        entity.setStockTotal(book.getStockLevel().total());
        entity.setStockReserved(book.getStockLevel().reserved());
        return entity;
    }

    static Category toCategoryDomain(CategoryJpaEntity entity) {
        return new Category(entity.getId(), entity.getName());
    }
}
