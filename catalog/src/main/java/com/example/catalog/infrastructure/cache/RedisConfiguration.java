package com.example.catalog.infrastructure.cache;

import com.example.catalog.application.query.book.BookDetailView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
class RedisConfiguration {

    @Bean
    RedisTemplate<String, BookDetailView> bookDetailRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, BookDetailView> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(BookDetailView.class));
        return template;
    }
}
