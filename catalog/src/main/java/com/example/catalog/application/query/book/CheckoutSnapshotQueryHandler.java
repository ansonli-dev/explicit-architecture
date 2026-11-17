package com.example.catalog.application.query.book;

import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.application.port.outbound.BookReadRepository;
import com.example.seedwork.application.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CheckoutSnapshotQueryHandler implements QueryHandler<CheckoutSnapshotQuery, List<CheckoutSnapshotQuery.Result>> {

    private final BookReadRepository books;

    @Override
    public List<CheckoutSnapshotQuery.Result> handle(CheckoutSnapshotQuery query) {
        var quantities = new TreeMap<UUID, Integer>(java.util.Comparator.comparing(UUID::toString));
        for (var line : query.lines()) {
            if (quantities.putIfAbsent(line.bookId(), line.quantity()) != null) {
                throw new IllegalArgumentException("Duplicate bookId");
            }
        }
        var snapshot = books.findAll(quantities.keySet()).stream()
                .map(book -> new CheckoutSnapshotQuery.Result(book.id(), book.title(), quantities.get(book.id()),
                        book.priceMinor(), book.currency(), book.stock()))
                .toList();
        if (snapshot.size() != quantities.size()) {
            snapshot.forEach(book -> quantities.remove(book.bookId()));
            throw new BookNotFoundException(quantities.firstKey());
        }
        return snapshot;
    }
}
