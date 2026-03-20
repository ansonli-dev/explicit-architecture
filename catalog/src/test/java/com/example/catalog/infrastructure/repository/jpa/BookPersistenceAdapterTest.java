package com.example.catalog.infrastructure.repository.jpa;

import com.example.catalog.domain.ports.BookPersistence;
import com.example.catalog.domain.model.Author;
import com.example.catalog.domain.model.Book;
import com.example.catalog.domain.model.BookId;
import com.example.catalog.domain.model.Category;
import com.example.catalog.domain.model.Money;
import com.example.catalog.domain.model.Title;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(BookPersistenceAdapter.class)
class BookPersistenceAdapterTest {

    @Autowired BookPersistence bookPersistence;

    private Book buildBook(String titleStr, String categoryName, int stock) {
        return Book.create(
                new Title(titleStr),
                new Author("Robert Martin", "Software engineer"),
                new Money(4999L, "CNY"),
                Category.create(categoryName),
                stock);
    }

    @Test
    void givenNewBook_whenSave_thenBookCanBeFoundById() {
        // Arrange
        Book book = buildBook("Clean Code", "Programming", 10);

        // Act
        Book saved = bookPersistence.save(book);

        // Assert
        Optional<Book> found = bookPersistence.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle().value()).isEqualTo("Clean Code");
        assertThat(found.get().getStockLevel().total()).isEqualTo(10);
    }

    @Test
    void givenNonExistentId_whenFindById_thenReturnsEmpty() {
        // Act
        Optional<Book> found = bookPersistence.findById(BookId.generate());

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void givenExistingBook_whenFindById_thenReturnsFalse() {
        // Arrange
        Book book = buildBook("Refactoring", "Programming", 5);
        bookPersistence.save(book);

        // Act + Assert
        assertThat(bookPersistence.existsById(book.getId())).isTrue();
        assertThat(bookPersistence.existsById(BookId.generate())).isFalse();
    }

    @Test
    void givenBooksInDifferentCategories_whenFindAllWithCategoryFilter_thenOnlyMatchingReturned() {
        // Arrange
        bookPersistence.save(buildBook("Book A", "Science", 3));
        bookPersistence.save(buildBook("Book B", "History", 5));
        bookPersistence.save(buildBook("Book C", "Science", 7));

        // Act
        List<Book> scienceBooks = bookPersistence.findAll(0, 20, "Science");
        List<Book> historyBooks = bookPersistence.findAll(0, 20, "History");
        List<Book> allBooks = bookPersistence.findAll(0, 20, null);

        // Assert
        assertThat(scienceBooks).hasSize(2);
        assertThat(historyBooks).hasSize(1);
        assertThat(allBooks.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void givenExistingBook_whenSaveWithUpdatedStock_thenStockUpdated() {
        // Arrange
        Book book = buildBook("DDD", "Programming", 10);
        bookPersistence.save(book);
        book.restock(5);

        // Act
        bookPersistence.save(book);

        // Assert
        Optional<Book> found = bookPersistence.findById(book.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStockLevel().total()).isEqualTo(15);
    }

    @Test
    void givenCategoryName_whenFindCategoryByName_thenReturnsCategoryIfExists() {
        // Arrange
        bookPersistence.save(buildBook("Any Book", "MyCategory", 1));

        // Act
        Optional<Category> found = bookPersistence.findCategoryByName("MyCategory");
        Optional<Category> notFound = bookPersistence.findCategoryByName("NonExistent");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("MyCategory");
        assertThat(notFound).isEmpty();
    }
}
