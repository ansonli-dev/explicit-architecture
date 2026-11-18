package com.example.catalog.interfaces.rest;

import com.example.catalog.application.port.inbound.GetStockUseCase;
import com.example.catalog.application.query.book.GetStockQuery;
import com.example.catalog.interfaces.rest.response.StockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
class StockQueryController {

    private final GetStockUseCase getStock;

    @GetMapping("/{id}/stock")
    StockResponse stock(@PathVariable UUID id) {
        return StockResponse.from(getStock.handle(new GetStockQuery(id)));
    }
}
