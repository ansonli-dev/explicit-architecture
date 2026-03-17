package com.example.catalog.domain.model;

import com.example.catalog.domain.event.BookAdded;
import com.example.catalog.domain.event.StockReleased;
import com.example.catalog.domain.event.StockReserved;
import com.example.seedwork.domain.AggregateRoot;

import java.time.Instant;
import java.util.UUID;

/**
 * Book — Aggregate Root of the catalog bounded context.
 * <p>
 * All business invariants (stock, pricing) are enforced here.
 * No framework dependencies — pure Java.
 */
public class Book extends AggregateRoot<BookId> {

    private Title title;
    private Author author;
    private Money price;
    private Category category;
    private StockLevel stockLevel;

    // Private constructor — use factory methods
    private Book(BookId id, Title title, Author author, Money price,
            Category category, StockLevel stockLevel) {
        super(id);
        this.title = title;
        this.author = author;
        this.price = price;
        this.category = category;
        this.stockLevel = stockLevel;
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    public static Book create(Title title, Author author, Money price,
            Category category, int initialStock) {
        BookId id = BookId.generate();
        StockLevel stock = StockLevel.of(initialStock);
        return new Book(id, title, author, price, category, stock);
    }

    /** Reconstitute from persistence — no events emitted */
    public static Book reconstitute(BookId id, Title title, Author author, Money price,
            Category category, StockLevel stockLevel) {
        return new Book(id, title, author, price, category, stockLevel);
    }

    // ─── Domain Behaviour ─────────────────────────────────────────────────────

    public BookAdded added() {
        return new BookAdded(UUID.randomUUID(), this.getId().value(), this.title.value(), Instant.now());
    }

    public void reserve(UUID orderId, int quantity) {
        this.stockLevel = this.stockLevel.reserve(quantity);
        registerEvent(new StockReserved(UUID.randomUUID(), this.getId().value(), orderId, quantity, Instant.now()));
    }

    public void release(UUID orderId, int quantity) {
        this.stockLevel = this.stockLevel.release(quantity);
        registerEvent(new StockReleased(UUID.randomUUID(), this.getId().value(), orderId, quantity, Instant.now()));
    }

    public void restock(int quantity) {
        this.stockLevel = this.stockLevel.restock(quantity);
    }

    public void updatePrice(Money newPrice) {
        if (newPrice == null)
            throw new IllegalArgumentException("Price must not be null");
        this.price = newPrice;
    }

    public void updateMetadata(Title newTitle, Author newAuthor) {
        if (newTitle != null)
            this.title = newTitle;
        if (newAuthor != null)
            this.author = newAuthor;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public Title getTitle() {
        return title;
    }

    public Author getAuthor() {
        return author;
    }

    public Money getPrice() {
        return price;
    }

    public Category getCategory() {
        return category;
    }

    public StockLevel getStockLevel() {
        return stockLevel;
    }
}
