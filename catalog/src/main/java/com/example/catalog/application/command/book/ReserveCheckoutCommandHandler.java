package com.example.catalog.application.command.book;

import com.example.catalog.application.port.outbound.CatalogWriteTransaction;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.CheckoutReservation;
import com.example.catalog.domain.ports.CheckoutReservationPersistence;
import com.example.catalog.domain.ports.LockedBookPersistence;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
class ReserveCheckoutCommandHandler implements CommandHandler<ReserveCheckoutCommand, ReserveCheckoutCommand.Result> {

    private final CatalogWriteTransaction transaction;
    private final CheckoutReservationPersistence reservations;
    private final LockedBookPersistence books;

    @Override
    public ReserveCheckoutCommand.Result handle(ReserveCheckoutCommand command) {
        String fingerprint = command.batch().fingerprint();
        return transaction.run(() -> reserve(command, fingerprint));
    }

    private ReserveCheckoutCommand.Result reserve(ReserveCheckoutCommand command, String fingerprint) {
        var lock = reservations.lockOrCreate(CheckoutReservation.pending(command.submissionId(), fingerprint));
        var reservation = lock.reservation();
        reservation.checkReplay(fingerprint);
        if (!lock.created()) {
            return new ReserveCheckoutCommand.Result(reservation.state(), reservation.reason());
        }

        Map<BookId, Book> lockedBooks = new HashMap<>();
        for (var line : command.batch().lines()) {
            books.lock(line.bookId()).ifPresent(book -> lockedBooks.put(line.bookId(), book.book()));
        }
        var rejection = command.batch().rejection(lockedBooks);
        if (rejection.isPresent()) {
            reservation = reservation.rejected(rejection.get());
        } else {
            for (var line : command.batch().lines()) {
                Book book = lockedBooks.get(line.bookId());
                book.reserve(command.submissionId(), line.quantity());
                books.saveLocked(book);
            }
            reservation = reservation.held();
            reservations.storeHeldLines(reservation, command.batch().lines().stream()
                    .map(line -> new CheckoutReservation.HeldLine(line.bookId(), line.quantity()))
                    .toList());
        }
        reservations.update(reservation);
        return new ReserveCheckoutCommand.Result(reservation.state(), reservation.reason());
    }
}
