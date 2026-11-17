package com.example.catalog.application.command.book;

import com.example.catalog.application.port.outbound.CatalogWriteTransaction;
import com.example.catalog.domain.model.CheckoutReservation;
import com.example.catalog.domain.ports.CheckoutReservationPersistence;
import com.example.catalog.domain.ports.LockedBookPersistence;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
class ReleaseCheckoutCommandHandler implements CommandHandler<ReleaseCheckoutCommand, ReleaseCheckoutCommand.Result> {

    private final CatalogWriteTransaction transaction;
    private final CheckoutReservationPersistence reservations;
    private final LockedBookPersistence books;

    @Override
    public ReleaseCheckoutCommand.Result handle(ReleaseCheckoutCommand command) {
        return transaction.run(() -> release(command.submissionId()));
    }

    private ReleaseCheckoutCommand.Result release(UUID id) {
        var lock = reservations.lockOrCreate(CheckoutReservation.tombstone(id));
        if (lock.created()) {
            return ReleaseCheckoutCommand.Result.MISSING;
        }
        var reservation = lock.reservation();
        if (reservation.state() == CheckoutReservation.State.RELEASED) {
            return ReleaseCheckoutCommand.Result.ALREADY_RELEASED;
        }
        if (reservation.state() == CheckoutReservation.State.HELD) {
            var lines = reservations.heldLines(reservation);
            if (lines.isEmpty()) {
                throw new IllegalStateException("Held checkout reservation has no lines: " + id);
            }
            for (var line : lines) {
                var book = books.lock(line.bookId()).orElseThrow().book();
                book.release(id, line.quantity());
                books.saveLocked(book);
            }
        }
        reservations.update(reservation.released());
        return ReleaseCheckoutCommand.Result.RELEASED;
    }
}
