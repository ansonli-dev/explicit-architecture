package com.example.seedwork.infrastructure.kafka;

import com.example.seedwork.infrastructure.kafka.retry.ConsumerRetryPersistenceAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.kafka.annotation.KafkaListener")
public class KafkaIdempotencyAutoConfiguration {

    @Bean
    ProcessedEventStore processedEventStore(ProcessedEventJpaRepository repo) {
        return new ProcessedEventStore(repo);
    }

    @Bean
    public KafkaMessageProcessor kafkaMessageProcessor(ProcessedEventStore processedEventStore,
                                                        ConsumerRetryPersistenceAdapter retryAdapter) {
        return new KafkaMessageProcessor(processedEventStore, retryAdapter);
    }
}
