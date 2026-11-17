package com.example.catalog.application.query.book;

import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.application.port.outbound.BookReadRepository;
import com.example.seedwork.application.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class GetBookV2QueryHandler implements QueryHandler<GetBookV2Query, BookV2Result> {

    private final BookReadRepository books;

    @Override
    public BookV2Result handle(GetBookV2Query query) {
        return books.find(query.id()).orElseThrow(() -> new BookNotFoundException(query.id()));
    }
}
