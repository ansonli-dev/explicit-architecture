package com.example.catalog.application.command.book;

import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.ports.CheckoutReservationPersistence;
import com.example.catalog.domain.ports.LockedBookPersistence;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class ReleaseLegacyStockCommandHandler implements CommandHandler<ReleaseLegacyStockCommand, Void> {

    private final CheckoutReservationPersistence reservations;
    private final LockedBookPersistence books;

    @Override
    public Void handle(ReleaseLegacyStockCommand command) {
        // KafkaMessageProcessor and its legacy retry processor already own the transaction.
        BookId id = BookId.of(command.bookId());
        var book = books.lock(id).orElseThrow(() -> new BookNotFoundException(id.value())).book();
        book.releaseLegacy(command.orderId(), command.quantity(), reservations.activeQuantity(id));
        books.saveLocked(book);
        return null;
    }
}
