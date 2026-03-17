package com.example.catalog.domain.model;

import com.example.seedwork.domain.DomainException;

public class InsufficientStockException extends DomainException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
