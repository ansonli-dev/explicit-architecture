package com.example.catalog.application.query.book;

import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.seedwork.application.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetStockQueryHandler implements QueryHandler<GetStockQuery, StockView> {

    private final BookPersistence repository;

    @Override
    public StockView handle(GetStockQuery query) {
        Book book = repository.findById(BookId.of(query.id()))
                .orElseThrow(() -> new BookNotFoundException(query.id()));
        return new StockView(book.getId().value(), book.getStockLevel().available());
    }
}
