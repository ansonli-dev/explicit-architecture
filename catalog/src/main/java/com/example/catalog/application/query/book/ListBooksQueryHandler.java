package com.example.catalog.application.query.book;

import com.example.catalog.application.port.outbound.BookReadRepository;
import com.example.seedwork.application.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class ListBooksQueryHandler implements QueryHandler<ListBooksQuery, ListBooksQuery.Result> {

    private final BookReadRepository books;

    @Override
    public ListBooksQuery.Result handle(ListBooksQuery query) {
        int size = query.size();
        var rows = books.search(query.category(), query.query(), (query.page() - 1) * size, size + 1);
        return new ListBooksQuery.Result(
                List.copyOf(rows.subList(0, Math.min(size, rows.size()))), query.page(), rows.size() > size);
    }
}
