package com.example.catalog.application.port.inbound;

import com.example.catalog.application.query.book.GetStockQuery;
import com.example.catalog.application.query.book.StockResult;

public interface GetStockUseCase {
    StockResult handle(GetStockQuery query);
}
