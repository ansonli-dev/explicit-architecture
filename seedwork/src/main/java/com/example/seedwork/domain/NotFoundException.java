package com.example.seedwork.domain;

/**
 * Base class for "aggregate not found" domain violations.
 * Maps to HTTP 404 via the seedwork {@code GlobalExceptionHandler}.
 *
 * <p>Extend in each bounded context:
 * <pre>{@code
 * public class OrderNotFoundException extends NotFoundException {
 *     public OrderNotFoundException(UUID id) {
 *         super("Order not found: " + id);
 *     }
 * }
 * }</pre>
 */
public abstract class NotFoundException extends DomainException {

    protected NotFoundException(String message) {
        super(message);
    }
}
