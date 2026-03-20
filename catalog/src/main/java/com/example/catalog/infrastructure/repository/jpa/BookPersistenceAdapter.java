package com.example.catalog.infrastructure.repository.jpa;

import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Category;
import com.example.catalog.infrastructure.cache.BookPersistedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class BookPersistenceAdapter implements BookPersistence {

    private final BookJpaRepository bookJpaRepository;
    private final CategoryJpaRepository categoryJpaRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BookEntityMapper mapper;
    private final PlatformTransactionManager txManager;

    @Override
    @Transactional
    public Book save(Book book) {
        CategoryJpaEntity catEntity = findOrCreateCategory(book.getCategory());

        // Mapper attaches domain events to the entity as part of mapping —
        // no separate attachDomainEvents() call needed here.
        BookJpaEntity entity = bookJpaRepository.findById(book.getId().value())
                .map(existing -> mapper.updateEntity(book, catEntity, existing))
                .orElseGet(() -> mapper.toNewEntity(book, catEntity));

        BookJpaEntity saved = bookJpaRepository.save(entity);
        // Clear after a confirmed successful save; keeps events available for retry if save throws.
        book.clearDomainEvents();

        // Publish after save so the AFTER_COMMIT listener can evict the cache.
        eventPublisher.publishEvent(new BookPersistedEvent(book.getId().value()));

        return mapper.toDomain(saved);
    }

    /**
     * Finds or creates a category row. If a concurrent transaction wins the INSERT first,
     * catches the constraint violation and re-fetches inside a {@code REQUIRES_NEW}
     * sub-transaction.
     *
     * <p>A {@link DataIntegrityViolationException} most commonly indicates a concurrent
     * INSERT on the unique {@code name} column, but could technically be triggered by other
     * constraint violations (e.g. a duplicate UUID, though astronomically unlikely). The
     * recovery is safe regardless: {@code .orElseThrow(() -> e)} re-throws the original
     * exception if the re-fetch finds nothing, so a non-race DIVE is not silently swallowed.
     *
     * <p>The REQUIRES_NEW transaction is necessary because the outer transaction is marked
     * rollback-only after the DIVE; a fresh connection is needed to see the concurrent
     * winner's committed row.
     */
    private CategoryJpaEntity findOrCreateCategory(Category category) {
        var requiresNewTx = new TransactionTemplate(txManager);
        requiresNewTx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        return categoryJpaRepository.findByName(category.getName())
                .orElseGet(() -> {
                    try {
                        return categoryJpaRepository.save(
                                new CategoryJpaEntity(category.getId(), category.getName()));
                    } catch (DataIntegrityViolationException e) {
                        return Objects.requireNonNull(
                                requiresNewTx.execute(status ->
                                        categoryJpaRepository.findByName(category.getName())
                                                .orElseThrow(() -> e)));
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Book> findById(BookId id) {
        return bookJpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Book> findAll(int page, int size, String category) {
        return bookJpaRepository
                .findAllWithCategory(category, PageRequest.of(page, size))
                .stream()
                .map(mapper::toDomain)
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
        return categoryJpaRepository.findByName(name).map(mapper::toCategoryDomain);
    }

    @Override
    @Transactional
    public Category saveCategory(Category category) {
        CategoryJpaEntity entity = new CategoryJpaEntity(category.getId(), category.getName());
        CategoryJpaEntity saved = categoryJpaRepository.save(entity);
        return mapper.toCategoryDomain(saved);
    }
}
