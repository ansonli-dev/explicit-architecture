package com.example.order.domain.model;

import com.example.seedwork.domain.DomainException;

import java.util.UUID;

public class InsufficientStockException extends DomainException {
    public InsufficientStockException(UUID bookId, int requested, int available) {
        super("Insufficient stock for book " + bookId
                + ": requested=" + requested + ", available=" + available);
    }
}
