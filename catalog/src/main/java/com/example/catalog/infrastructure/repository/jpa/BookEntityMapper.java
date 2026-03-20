package com.example.catalog.infrastructure.repository.jpa;

import com.example.catalog.domain.model.Author;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.StockLevel;
import com.example.catalog.domain.model.Title;
import org.springframework.stereotype.Component;

/**
 * Maps between {@link Book} domain objects and {@link BookJpaEntity} JPA entities.
 *
 * <p>Unlike a static utility class, making this a Spring {@code @Component} lets the
 * mapping step own the domain-event transfer contract:
 * {@link #toNewEntity} and {@link #updateEntity} both call
 * {@link BookJpaEntity#attachDomainEvents} automatically, so the persistence adapter
 * never has to remember that step explicitly.
 *
 * <p>After a successful {@code save()}, the adapter is still responsible for calling
 * {@code book.clearDomainEvents()} — this is intentional: clearing belongs to the
 * adapter because it is the one that can confirm the save succeeded.
 */
@Component
class BookEntityMapper {

    /** Reconstitutes a domain {@link Book} from the JPA projection. */
    Book toDomain(BookJpaEntity entity) {
        return Book.reconstitute(
                BookId.of(entity.getId()),
                new Title(entity.getTitle()),
                new Author(entity.getAuthorName(), entity.getAuthorBiography()),
                new Money(entity.getPriceCents(), entity.getCurrency()),
                new Category(entity.getCategory().getId(), entity.getCategory().getName()),
                new StockLevel(entity.getStockTotal(), entity.getStockReserved()));
    }

    /**
     * Builds a brand-new {@link BookJpaEntity} and attaches any pending domain events
     * from the aggregate so that Spring Data can publish them on {@code save()}.
     */
    BookJpaEntity toNewEntity(Book book, CategoryJpaEntity category) {
        BookJpaEntity entity = new BookJpaEntity();
        applyFields(book, category, entity);
        entity.attachDomainEvents(book.peekDomainEvents());
        return entity;
    }

    /**
     * Updates an existing {@link BookJpaEntity} in-place and attaches any pending
     * domain events from the aggregate.
     */
    BookJpaEntity updateEntity(Book book, CategoryJpaEntity category, BookJpaEntity existing) {
        applyFields(book, category, existing);
        existing.attachDomainEvents(book.peekDomainEvents());
        return existing;
    }

    Category toCategoryDomain(CategoryJpaEntity entity) {
        return new Category(entity.getId(), entity.getName());
    }

    private void applyFields(Book book, CategoryJpaEntity category, BookJpaEntity entity) {
        entity.setId(book.getId().value());
        entity.setTitle(book.getTitle().value());
        entity.setAuthorName(book.getAuthor().name());
        entity.setAuthorBiography(book.getAuthor().biography());
        entity.setPriceCents(book.getPrice().cents());
        entity.setCurrency(book.getPrice().currency());
        entity.setCategory(category);
        entity.setStockTotal(book.getStockLevel().total());
        entity.setStockReserved(book.getStockLevel().reserved());
    }
}
