package com.example.order.application.checkout;

public final class CheckoutProblem extends RuntimeException {

    public enum Code {
        INVALID_REQUEST, EMAIL_UNVERIFIED, NOT_FOUND, BOOK_NOT_FOUND, QUOTE_EXPIRED, QUOTE_CONSUMED,
        IDEMPOTENCY_CONFLICT, INSUFFICIENT_STOCK, UNSUPPORTED_CURRENCY, AMOUNT_OUT_OF_RANGE,
        INTAKE_CLOSED, CATALOG_UNAVAILABLE, CATALOG_DATA_INVALID, VERSION_CONFLICT, ORDER_STATE_CONFLICT
    }

    private final Code code;

    public CheckoutProblem(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
