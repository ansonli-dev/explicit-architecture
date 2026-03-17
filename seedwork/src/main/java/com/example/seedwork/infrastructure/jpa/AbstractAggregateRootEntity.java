package com.example.seedwork.infrastructure.jpa;

import com.example.seedwork.domain.DomainEvent;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Base class for JPA entities that participate in Spring Data's domain event
 * publishing mechanism — without inheriting {@code AbstractAggregateRoot}.
 *
 * <p>Spring Data recognises {@link DomainEvents} and {@link AfterDomainEventPublication}
 * by annotation scanning, so no superclass inheritance is required.
 *
 * <p>Usage in a persistence adapter:
 * <pre>{@code
 * OrderJpaEntity entity = toEntity(order);
 * entity.attachDomainEvents(order.pullDomainEvents());  // transfer before save
 * orderJpaRepository.save(entity);
 * // Spring Data calls @DomainEvents, publishes to ApplicationContext,
 * // then calls @AfterDomainEventPublication to clear the list.
 * }</pre>
 */
public abstract class AbstractAggregateRootEntity {

    @Transient
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * Transfers domain events from the aggregate to this entity so that
     * Spring Data publishes them after {@code save()} completes.
     */
    public void attachDomainEvents(Collection<? extends DomainEvent> events) {
        domainEvents.addAll(events);
    }

    @DomainEvents
    Collection<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    @AfterDomainEventPublication
    void clearDomainEvents() {
        domainEvents.clear();
    }
}
