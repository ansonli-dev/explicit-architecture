package com.example.catalog.domain.ports;

import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.CheckoutReservation;

import java.util.List;

public interface CheckoutReservationPersistence {

    /** Creates and locks the row for a new submission, or locks and returns its existing decision. */
    Locked lockOrCreate(CheckoutReservation initial);

    void update(CheckoutReservation reservation);

    void storeHeldLines(CheckoutReservation reservation, List<CheckoutReservation.HeldLine> lines);

    List<CheckoutReservation.HeldLine> heldLines(CheckoutReservation reservation);

    int activeQuantity(BookId bookId);

    record Locked(CheckoutReservation reservation, boolean created) {}
}
