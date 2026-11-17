package com.example.catalog.application.command.book;

import com.example.seedwork.application.command.Command;

import java.util.UUID;

public record UpdateBookTitleV2Command(UUID bookId, String title, long expectedVersion)
        implements Command<UpdateBookTitleV2Command.Result> {

    public record Result(UUID id, String title, String author, String category, long priceMinor,
                         String currency, int stock, long version) {}
}
