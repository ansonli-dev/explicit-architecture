package com.example.catalog.application.port.inbound;

import com.example.catalog.application.query.book.BookDetailView;
import com.example.catalog.application.query.book.GetBookQuery;

public interface GetBookUseCase {
    BookDetailView handle(GetBookQuery query);
}
