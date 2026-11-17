package com.example.catalog.application.query.book;

import com.example.catalog.application.port.outbound.BookReadRepository;
import com.example.seedwork.application.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ListCategoriesQueryHandler implements QueryHandler<ListCategoriesQuery, List<String>> {

    private final BookReadRepository books;

    @Override
    public List<String> handle(ListCategoriesQuery query) {
        return books.categories();
    }
}
