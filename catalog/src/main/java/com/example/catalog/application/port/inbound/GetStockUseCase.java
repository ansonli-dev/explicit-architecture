package com.example.catalog.application.port.inbound;

import com.example.catalog.application.query.book.GetStockQuery;
import com.example.catalog.application.query.book.StockView;

public interface GetStockUseCase {
    StockView handle(GetStockQuery query);
}
