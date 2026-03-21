package com.example.catalog.application.port.inbound;

import com.example.catalog.application.command.book.AddBookCommand;
import com.example.catalog.application.command.book.AddBookResult;

public interface AddBookUseCase {
    AddBookResult handle(AddBookCommand command);
}
