package com.example.seedwork.infrastructure.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Outbox infrastructure configuration — auto-discovered via Spring Boot auto-configuration.
 * Also owns {@code @EntityScan} and {@code @EnableJpaRepositories} for the
 * {@code com.example} namespace, so services need no extra scan annotations.
 *
 * <h3>Relay strategy</h3>
 * Configure via {@code outbox.relay.strategy} (default: {@code scheduler}):
 * <ul>
 *   <li>{@code scheduler} — {@link OutboxRelayScheduler} polls the outbox table on a fixed
 *       interval and publishes to Kafka directly. Suitable for local development and tests.
 *       Tune with {@code outbox.relay.interval-ms} (default: 5000).</li>
 *   <li>{@code debezium} — Debezium CDC reads the PostgreSQL WAL and publishes outbox rows
 *       to Kafka. The scheduler bean is NOT created; Debezium handles relay externally.</li>
 * </ul>
 *
 * <h3>Flyway setup required in each service</h3>
 * Seedwork provides base table DDL under {@code classpath:db/seedwork/}.
 * Add the following to each service's {@code application.yml}:
 * <pre>{@code
 * spring:
 *   flyway:
 *     locations: classpath:db/migration,classpath:db/seedwork
 * }</pre>
 * Seedwork migrations use versions {@code V0001–V0099}.
 * Service-specific migrations must start from {@code V0100} to avoid conflicts.
 *
 * <h3>Metrics</h3>
 * Registers a {@code seedwork.outbox.unpublished} gauge (Micrometer) that tracks
 * the number of unpublished outbox rows. Use this for alerting on relay lag.
 */
@AutoConfiguration
@EntityScan("com.example")
@EnableJpaRepositories("com.example")
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnBean(OutboxMapper.class)
    public OutboxWriteListener outboxWriteListener(OutboxJpaRepository repo, OutboxMapper mapper) {
        return new OutboxWriteListener(repo, mapper);
    }

    @Bean
    public KafkaOutboxEventPublisher kafkaOutboxEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        return new KafkaOutboxEventPublisher(kafkaTemplate);
    }

    /**
     * Created only when {@code outbox.relay.strategy=scheduler} (the default).
     * When {@code outbox.relay.strategy=debezium} this bean is skipped and Debezium
     * CDC handles relay externally by tailing the PostgreSQL WAL.
     */
    @Bean
    @ConditionalOnProperty(name = "outbox.relay.strategy", havingValue = "scheduler", matchIfMissing = true)
    public OutboxRelayScheduler outboxRelayScheduler(OutboxJpaRepository repo,
                                                     KafkaOutboxEventPublisher publisher) {
        return new OutboxRelayScheduler(repo, publisher);
    }

    @Bean
    public OutboxMetrics outboxMetrics(OutboxJpaRepository repo, MeterRegistry meterRegistry) {
        return new OutboxMetrics(repo, meterRegistry);
    }
}
