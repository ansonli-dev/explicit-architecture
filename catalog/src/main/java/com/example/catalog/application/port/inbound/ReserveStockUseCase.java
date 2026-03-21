package com.example.catalog.application.port.inbound;

import com.example.catalog.application.command.book.ReserveStockCommand;
import com.example.catalog.application.command.book.ReserveStockResult;

public interface ReserveStockUseCase {
    ReserveStockResult handle(ReserveStockCommand command);
}
