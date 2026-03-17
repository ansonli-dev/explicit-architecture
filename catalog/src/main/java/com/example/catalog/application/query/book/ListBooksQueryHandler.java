package com.example.catalog.application.query.book;

import com.example.catalog.application.port.outbound.BookPersistence;
import com.example.seedwork.application.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListBooksQueryHandler implements QueryHandler<ListBooksQuery, List<BookResponse>> {

    private final BookPersistence repository;

    @Override
    public List<BookResponse> handle(ListBooksQuery query) {
        return repository.findAll(query.page(), query.size(), query.category()).stream()
                .map(b -> new BookResponse(b.getId().value(), b.getTitle().value(), b.getAuthor().name(),
                        b.getCategory().getName(), b.getPrice() != null ? b.getPrice().cents() : 0,
                        b.getPrice() != null ? b.getPrice().currency() : "USD", b.getStockLevel().available()))
                .collect(Collectors.toList());
    }
}
