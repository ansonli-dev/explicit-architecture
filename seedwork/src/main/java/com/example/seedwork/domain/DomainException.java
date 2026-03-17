package com.example.seedwork.domain;

/**
 * Base class for all domain rule violations.
 *
 * <p>Extend this in each bounded context's domain layer to give violations
 * a common, catch-able supertype without leaking service-specific types:
 *
 * <pre>{@code
 * public class OrderStateException extends DomainException {
 *     public OrderStateException(String message) { super(message); }
 * }
 * }</pre>
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
