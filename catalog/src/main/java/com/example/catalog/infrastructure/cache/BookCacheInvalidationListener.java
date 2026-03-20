package com.example.catalog.infrastructure.cache;

import com.example.catalog.application.port.outbound.BookCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts the book cache entry after the book-save transaction commits.
 *
 * <p>Using {@code AFTER_COMMIT} ensures the cache is invalidated only when the
 * database write is durable — a rollback leaves the cache unaffected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BookCacheInvalidationListener {

    private final BookCache cache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookPersisted(BookPersistedEvent event) {
        try {
            cache.invalidate(event.bookId());
        } catch (RuntimeException e) {
            log.warn("Cache invalidation failed for bookId={} — stale data may be served temporarily",
                    event.bookId(), e);
        }
    }
}
