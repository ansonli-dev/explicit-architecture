package com.example.catalog.application.query.book;

import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.seedwork.application.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetStockQueryHandler implements QueryHandler<GetStockQuery, StockResponse> {

    private final BookPersistence repository;

    @Override
    public StockResponse handle(GetStockQuery query) {
        Book book = repository.findById(BookId.of(query.id()))
                .orElseThrow(() -> new BookNotFoundException(query.id()));
        return new StockResponse(book.getId().value(), book.getStockLevel().available());
    }
}
