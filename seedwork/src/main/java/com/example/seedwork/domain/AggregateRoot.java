package com.example.seedwork.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Base class for all aggregate roots.
 *
 * <p>Provides:
 * <ul>
 *   <li>ID-based structural equality</li>
 *   <li>In-process domain event collection — subclasses call {@link #registerEvent}
 *       from their state-transition methods; the persistence adapter calls
 *       {@link #pullDomainEvents} once inside the same transaction to write
 *       the outbox entries atomically.</li>
 * </ul>
 *
 * <p>Subclasses must call {@code super(id)} from their constructors.
 *
 * @param <ID> the strongly-typed identifier, must implement {@link DomainId}
 */
public abstract class AggregateRoot<ID extends DomainId<?>> {

    private final ID id;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot(ID id) {
        Objects.requireNonNull(id, "Aggregate ID must not be null");
        this.id = id;
    }

    public ID getId() {
        return id;
    }

    /**
     * Records a domain event raised by this aggregate.
     * Called from state-transition methods (e.g., {@code place()}, {@code cancel()}).
     */
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /**
     * Returns all recorded domain events WITHOUT clearing the internal list.
     * <p>
     * Prefer this method in persistence adapters over {@link #pullDomainEvents()} so that
     * domain events survive a failed {@code jpaRepository.save()} call and can be retried:
     * <pre>{@code
     * entity.attachDomainEvents(aggregate.peekDomainEvents()); // peek, don't clear
     * jpaRepository.save(entity);                              // if this throws, events are still on aggregate
     * aggregate.clearDomainEvents();                           // only clear after successful save
     * }</pre>
     */
    public List<DomainEvent> peekDomainEvents() {
        return Collections.unmodifiableList(new ArrayList<>(domainEvents));
    }

    /**
     * Clears the internal domain event list.
     * Call this explicitly after a successful {@code jpaRepository.save()} when using
     * {@link #peekDomainEvents()}.
     */
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    /**
     * Returns all recorded domain events and clears the internal list.
     *
     * @deprecated Prefer {@link #peekDomainEvents()} + {@link #clearDomainEvents()} so that
     * events survive a failed save and can be retried. This method is kept for backward
     * compatibility but should not be used in new persistence adapters.
     */
    @Deprecated
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> snapshot = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return snapshot;
    }

    /**
     * Two aggregates are equal when they are of the same concrete type and
     * have the same ID. {@code getClass()} is intentional — an {@code Order}
     * and a {@code Book} with accidentally equal UUIDs must NOT be equal.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AggregateRoot<?> that = (AggregateRoot<?>) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
