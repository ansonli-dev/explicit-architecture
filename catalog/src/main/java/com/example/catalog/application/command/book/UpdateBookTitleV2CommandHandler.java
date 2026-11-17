package com.example.catalog.application.command.book;

import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.application.CatalogDataException;
import com.example.catalog.application.port.outbound.CatalogWriteTransaction;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Title;
import com.example.catalog.domain.ports.LockedBookPersistence;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class UpdateBookTitleV2CommandHandler implements
        CommandHandler<UpdateBookTitleV2Command, UpdateBookTitleV2Command.Result> {

    private final LockedBookPersistence books;
    private final CatalogWriteTransaction transaction;

    @Override
    public UpdateBookTitleV2Command.Result handle(UpdateBookTitleV2Command command) {
        var title = new Title(command.title());
        return transaction.run(() -> {
            var locked = books.lock(BookId.of(command.bookId()))
                    .orElseThrow(() -> new BookNotFoundException(command.bookId()));
            var book = locked.book();
            locked.rename(title, command.expectedVersion());
            CatalogDataException.checkPrice(book.getPrice().cents(), book.getPrice().currency());
            long version = books.saveLocked(book);
            CatalogDataException.checkInteger(version);
            return new UpdateBookTitleV2Command.Result(book.getId().value(), book.getTitle().value(),
                    book.getAuthor().name(), book.getCategory().getName(), book.getPrice().cents(),
                    book.getPrice().currency(), book.getStockLevel().available(), version);
        });
    }
}
