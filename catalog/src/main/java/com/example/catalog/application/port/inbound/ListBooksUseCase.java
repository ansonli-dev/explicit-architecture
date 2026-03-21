package com.example.catalog.application.port.inbound;

import com.example.catalog.application.query.book.BookSummaryView;
import com.example.catalog.application.query.book.ListBooksQuery;

import java.util.List;

public interface ListBooksUseCase {
    List<BookSummaryView> handle(ListBooksQuery query);
}
