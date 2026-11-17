package com.example.catalog.interfaces.messaging.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class OrderConsumerConfiguration {

    @Bean
    DefaultErrorHandler orderConsumerErrorHandler() {
        // A failed v2 release remains at its offset until the database transaction succeeds.
        var handler = new DefaultErrorHandler(new FixedBackOff(1_000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.setAckAfterHandle(false);
        return handler;
    }
}
