package com.example.seedwork.domain;

/**
 * Marker interface for strongly-typed domain identifiers.
 *
 * <p>Implement this on a Java record whose component is named {@code value};
 * the record's auto-generated accessor satisfies the contract with zero extra code:
 *
 * <pre>{@code
 * public record OrderId(UUID value) implements DomainId<UUID> { ... }
 * }</pre>
 *
 * @param <T> the backing primitive type (typically {@code UUID})
 */
public interface DomainId<T> {
    T value();
}
