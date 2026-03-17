package com.example.order.domain.model;

import com.example.seedwork.domain.DomainException;

public class OrderStateException extends DomainException {
    public OrderStateException(String message) {
        super(message);
    }
}
