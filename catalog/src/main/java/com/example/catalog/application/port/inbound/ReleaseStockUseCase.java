package com.example.catalog.application.port.inbound;

import com.example.catalog.application.command.book.ReleaseStockCommand;

public interface ReleaseStockUseCase {
    void handle(ReleaseStockCommand command);
}
