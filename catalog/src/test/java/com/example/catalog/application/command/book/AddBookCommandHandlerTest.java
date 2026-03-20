package com.example.catalog.application.command.book;

import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.domain.model.Book;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AddBookCommandHandlerTest {

    @Mock BookPersistence bookRepository;

    private AddBookCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AddBookCommandHandler(bookRepository);
    }

    @Test
    void givenValidCommand_whenHandle_thenBookSavedAndResultReturned() {
        var command = new AddBookCommand(
                "Clean Code", "Robert Martin", "Uncle Bob",
                4500L, "CNY", "Tech", 10);

        AddBookResult result = handler.handle(command);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Clean Code");
        assertThat(result.availableStock()).isEqualTo(10);
        verify(bookRepository).save(any(Book.class));
    }
}
