package com.example.seedwork.infrastructure.kafka.retry;

import com.example.seedwork.infrastructure.kafka.ProcessedEventStore;
import com.example.seedwork.infrastructure.kafka.RetryHandlerRegistry;
import com.example.seedwork.infrastructure.kafka.RetryableKafkaHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "consumer.retry", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(RetryProperties.class)
@EnableScheduling
public class ConsumerRetryAutoConfiguration {

    @Bean
    ConsumerRetryPersistenceAdapter consumerRetryPersistenceAdapter(
            ConsumerRetryEventJpaRepository repo) {
        return new ConsumerRetryPersistenceAdapter(repo);
    }

    @Bean
    public RetryHandlerRegistry retryHandlerRegistry(List<RetryableKafkaHandler<?>> handlers) {
        return new RetryHandlerRegistry(handlers);
    }

    @Bean
    RetryEntryProcessor retryEntryProcessor(ConsumerRetryEventJpaRepository retryRepository,
                                             ProcessedEventStore processedEventStore,
                                             ConsumerRetryPersistenceAdapter retryAdapter,
                                             RetryHandlerRegistry handlerRegistry,
                                             RetryProperties props,
                                             MeterRegistry meterRegistry,
                                             ApplicationEventPublisher eventPublisher) {
        return new RetryEntryProcessor(retryRepository, processedEventStore, retryAdapter,
                handlerRegistry, props, meterRegistry, eventPublisher);
    }

    @Bean
    RetryScheduler retryScheduler(RetryEntryProcessor entryProcessor) {
        return new RetryScheduler(entryProcessor);
    }
}
