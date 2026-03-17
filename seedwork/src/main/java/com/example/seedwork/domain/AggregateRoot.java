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
     * Returns all recorded domain events and clears the internal list.
     * <p>
     * Must be called exactly once per save, inside the persistence adapter's
     * {@code @Transactional} boundary so that the outbox write and the aggregate
     * state write happen atomically.
     */
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
