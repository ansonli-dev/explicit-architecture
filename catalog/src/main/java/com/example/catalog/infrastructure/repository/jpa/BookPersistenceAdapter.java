package com.example.catalog.infrastructure.repository.jpa;

import com.example.catalog.application.port.outbound.BookPersistence;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Category;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class BookPersistenceAdapter implements BookPersistence {

    private final BookJpaRepository bookJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;

    BookPersistenceAdapter(BookJpaRepository bookJpaRepository,
            CategoryJpaRepository categoryJpaRepository) {
        this.bookJpaRepository = bookJpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Override
    @Transactional
    public Book save(Book book) {
        CategoryJpaEntity catEntity = categoryJpaRepository.findByName(book.getCategory().getName())
                .orElseGet(() -> categoryJpaRepository.save(
                        new CategoryJpaEntity(book.getCategory().getId(), book.getCategory().getName())));

        BookJpaEntity entity = bookJpaRepository.findById(book.getId().value())
                .map(existing -> {
                    // Update existing — preserve @Version to avoid INSERT/duplicate-key
                    existing.setTitle(book.getTitle().value());
                    existing.setAuthorName(book.getAuthor().name());
                    existing.setAuthorBiography(book.getAuthor().biography());
                    existing.setPriceCents(book.getPrice().cents());
                    existing.setCurrency(book.getPrice().currency());
                    existing.setCategory(catEntity);
                    existing.setStockTotal(book.getStockLevel().total());
                    existing.setStockReserved(book.getStockLevel().reserved());
                    return existing;
                })
                .orElseGet(() -> BookMapper.toEntity(book, catEntity));

        entity.attachDomainEvents(book.pullDomainEvents());
        BookJpaEntity saved = bookJpaRepository.save(entity);
        return BookMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Book> findById(BookId id) {
        return bookJpaRepository.findById(id.value()).map(BookMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> findAll(int page, int size, String category) {
        return bookJpaRepository
                .findAllWithCategory(category, PageRequest.of(page, size))
                .stream()
                .map(BookMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(BookId id) {
        return bookJpaRepository.existsById(id.value());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findCategoryByName(String name) {
        return categoryJpaRepository.findByName(name).map(BookMapper::toCategoryDomain);
    }

    @Override
    @Transactional
    public Category saveCategory(Category category) {
        CategoryJpaEntity entity = new CategoryJpaEntity(category.getId(), category.getName());
        CategoryJpaEntity saved = categoryJpaRepository.save(entity);
        return BookMapper.toCategoryDomain(saved);
    }
}
