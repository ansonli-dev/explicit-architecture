package com.example.catalog.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record CheckoutReservationBatch(List<Line> lines) {

    public CheckoutReservationBatch {
        if (lines.isEmpty() || lines.size() > 20) {
            throw new IllegalArgumentException("Reservation requires 1 to 20 lines");
        }
        var seen = new HashSet<BookId>();
        if (!lines.stream().allMatch(line -> seen.add(line.bookId()))) {
            throw new IllegalArgumentException("Duplicate bookId");
        }
        // UUID text order matches PostgreSQL's unsigned UUID order.
        lines = lines.stream().sorted(Comparator.comparing(line -> line.bookId().toString())).toList();
    }

    public Optional<CheckoutReservation.Reason> rejection(Map<BookId, Book> books) {
        for (var line : lines) {
            Book book = books.get(line.bookId());
            if (book == null) {
                return Optional.of(CheckoutReservation.Reason.BOOK_NOT_FOUND);
            }
            if (book.getPrice().cents() != line.unitPriceMinor() || !book.getPrice().currency().equals(line.currency())) {
                return Optional.of(CheckoutReservation.Reason.PRICE_CHANGED);
            }
            if (!book.getStockLevel().canReserve(line.quantity())) {
                return Optional.of(CheckoutReservation.Reason.INSUFFICIENT_STOCK);
            }
        }
        return Optional.empty();
    }

    public String fingerprint() {
        String canonical = lines.stream()
                .map(line -> "%s|%s|%s|%s".formatted(line.bookId(), line.quantity(), line.unitPriceMinor(), line.currency()))
                .collect(Collectors.joining("\n"));
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Line(BookId bookId, int quantity, long unitPriceMinor, String currency) {
        public Line {
            if (bookId == null || quantity < 1 || quantity > 99 || unitPriceMinor < 0
                    || unitPriceMinor > 9_007_199_254_740_991L || currency == null || !currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Invalid reservation line");
            }
        }
    }
}
