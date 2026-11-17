package com.example.catalog.application;

/** Stored Catalog data that cannot be represented in the v2 contract. */
public final class CatalogDataException extends RuntimeException {

    public static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    private CatalogDataException() {
        super("Catalog value is outside the v2 contract");
    }

    public static void checkPrice(long amount, String currency) {
        checkInteger(amount);
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new CatalogDataException();
        }
    }

    public static void checkInteger(long value) {
        if (value < 0 || value > MAX_SAFE_INTEGER) {
            throw new CatalogDataException();
        }
    }
}
