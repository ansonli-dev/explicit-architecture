package com.example.catalog.infrastructure.cache;

import java.util.UUID;

/**
 * Application event published by {@link com.example.catalog.infrastructure.repository.jpa.BookPersistenceAdapter}
 * after every successful book save. Consumed by {@link BookCacheInvalidationListener} in the
 * {@code AFTER_COMMIT} transaction phase to evict the stale cache entry.
 *
 * <p>Package-private — this event is an internal coordination mechanism between the
 * persistence and cache adapters; no other layer should depend on it.
 */
public record BookPersistedEvent(UUID bookId) {
}
