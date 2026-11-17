package com.example.catalog.domain.model;

public record VersionedBook(Book book, long version) {

    public void rename(Title title, long expectedVersion) {
        if (version != expectedVersion) {
            throw new VersionConflict();
        }
        book.updateMetadata(title, null);
    }

    public static final class VersionConflict extends RuntimeException {}
}
