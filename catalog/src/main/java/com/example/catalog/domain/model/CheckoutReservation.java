package com.example.catalog.domain.model;

import java.util.List;
import java.util.UUID;

public record CheckoutReservation(UUID submissionId, String fingerprint, State state, Reason reason) {

    public enum State { PENDING, HELD, REJECTED, RELEASED }
    public enum Reason { BOOK_NOT_FOUND, PRICE_CHANGED, INSUFFICIENT_STOCK }

    public static CheckoutReservation pending(UUID id, String fingerprint) {
        return new CheckoutReservation(id, fingerprint, State.PENDING, null);
    }

    public static CheckoutReservation tombstone(UUID id) {
        return new CheckoutReservation(id, null, State.RELEASED, null);
    }

    public CheckoutReservation held() {
        return new CheckoutReservation(submissionId, fingerprint, State.HELD, null);
    }

    public CheckoutReservation rejected(Reason reason) {
        return new CheckoutReservation(submissionId, fingerprint, State.REJECTED, reason);
    }

    public CheckoutReservation released() {
        return new CheckoutReservation(submissionId, fingerprint, State.RELEASED, null);
    }

    public void checkReplay(String requestFingerprint) {
        if (fingerprint != null && !fingerprint.equals(requestFingerprint)) {
            throw new PayloadConflict();
        }
    }

    public record HeldLine(BookId bookId, int quantity) {}

    public static final class PayloadConflict extends RuntimeException {}
}
