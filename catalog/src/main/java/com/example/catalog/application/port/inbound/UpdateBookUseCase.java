package com.example.catalog.application.port.inbound;

import com.example.catalog.application.command.book.UpdateBookCommand;
import com.example.catalog.application.command.book.UpdateBookResult;

public interface UpdateBookUseCase {
    UpdateBookResult handle(UpdateBookCommand command);
}
