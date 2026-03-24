package com.example.catalog.application.port.inbound;

import com.example.catalog.application.query.book.BookDetailResult;
import com.example.catalog.application.query.book.GetBookQuery;

public interface GetBookUseCase {
    BookDetailResult handle(GetBookQuery query);
}
