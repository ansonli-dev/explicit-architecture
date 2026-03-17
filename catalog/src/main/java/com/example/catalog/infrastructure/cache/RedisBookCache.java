package com.example.catalog.infrastructure.cache;

import com.example.catalog.application.port.outbound.BookCache;
import com.example.catalog.application.query.book.BookDetailResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary adapter implementing BookCache port using Redis.
 * Cache TTL: 5 minutes. Key pattern: book:{bookId}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBookCache implements BookCache {

    private static final String KEY_PREFIX = "book:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, BookDetailResponse> redisTemplate;

    @Override
    public Optional<BookDetailResponse> get(UUID bookId) {
        BookDetailResponse cached = redisTemplate.opsForValue().get(key(bookId));
        if (cached != null) {
            log.debug("Cache hit: bookId={}", bookId);
        }
        return Optional.ofNullable(cached);
    }

    @Override
    public void put(UUID id, BookDetailResponse book) {
        redisTemplate.opsForValue().set(key(id), book, TTL);
        log.debug("Cached book: id={}", id);
    }

    @Override
    public void invalidate(UUID bookId) {
        redisTemplate.delete(key(bookId));
        log.debug("Evicted from cache: bookId={}", bookId);
    }

    private String key(UUID bookId) {
        return KEY_PREFIX + bookId.toString();
    }
}
