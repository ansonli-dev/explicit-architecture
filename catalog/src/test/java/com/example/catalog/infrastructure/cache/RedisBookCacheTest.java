package com.example.catalog.infrastructure.cache;

import com.example.catalog.application.query.book.BookDetailResult;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Import({RedisConfiguration.class, RedisBookCache.class})
@Testcontainers
class RedisBookCacheTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired RedisBookCache redisBookCache;

    private final UUID bookId = UUID.randomUUID();

    @Test
    void givenNoCachedValue_whenGet_thenReturnsEmpty() {
        // Act
        Optional<BookDetailResult> result = redisBookCache.get(UUID.randomUUID());

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void givenBookPut_whenGet_thenReturnsCachedValue() {
        // Arrange
        var book = new BookDetailResult(bookId, "Clean Code", "Robert Martin", "Programming", 4999L, "CNY", 100);
        redisBookCache.put(bookId, book);

        // Act
        Optional<BookDetailResult> result = redisBookCache.get(bookId);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(bookId);
        assertThat(result.get().title()).isEqualTo("Clean Code");
    }

    @Test
    void givenCachedBook_whenInvalidate_thenGetReturnsEmpty() {
        // Arrange
        UUID id = UUID.randomUUID();
        var book = new BookDetailResult(id, "Refactoring", "Martin Fowler", "Programming", 5999L, "CNY", 50);
        redisBookCache.put(id, book);

        // Act
        redisBookCache.invalidate(id);

        // Assert
        assertThat(redisBookCache.get(id)).isEmpty();
    }
}
