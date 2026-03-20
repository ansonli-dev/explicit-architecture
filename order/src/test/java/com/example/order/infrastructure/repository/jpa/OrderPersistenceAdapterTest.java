package com.example.order.infrastructure.repository.jpa;

import com.example.order.domain.ports.OrderPersistence;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderId;
import com.example.order.domain.model.OrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(OrderPersistenceAdapter.class)
class OrderPersistenceAdapterTest {

    @Autowired OrderPersistence orderPersistence;

    private Order buildOrder(UUID bookId) {
        OrderItem item = OrderItem.create(bookId, "Clean Code", new Money(4999L, "CNY"), 2);
        return Order.create(
                CustomerId.of(UUID.randomUUID()),
                "user@example.com",
                List.of(item),
                new Money(9998L, "CNY"));
    }

    @Test
    void givenNewOrder_whenSave_thenOrderCanBeFoundById() {
        // Arrange
        Order order = buildOrder(UUID.randomUUID());

        // Act
        orderPersistence.save(order);

        // Assert
        Optional<Order> found = orderPersistence.findById(order.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCustomerEmail()).isEqualTo("user@example.com");
        assertThat(found.get().getTotalAmount().cents()).isEqualTo(9998L);
        assertThat(found.get().getItems()).hasSize(1);
    }

    @Test
    void givenNonExistentId_whenFindById_thenReturnsEmpty() {
        // Act
        Optional<Order> found = orderPersistence.findById(OrderId.of(UUID.randomUUID()));

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void givenPendingOrder_whenCancelAndSave_thenStatusPersistedAsCancelled() {
        // Arrange
        Order order = buildOrder(UUID.randomUUID());
        orderPersistence.save(order);
        order.cancel("Customer changed mind");

        // Act
        orderPersistence.save(order);

        // Assert
        Optional<Order> found = orderPersistence.findById(order.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus().name()).isEqualTo("CANCELLED");
    }

    @Test
    void givenOrderWithItems_whenFindById_thenItemsRestoredCorrectly() {
        // Arrange
        UUID bookId = UUID.randomUUID();
        Order order = buildOrder(bookId);
        orderPersistence.save(order);

        // Act
        Order found = orderPersistence.findById(order.getId()).orElseThrow();

        // Assert
        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getItems().get(0).bookId()).isEqualTo(bookId);
        assertThat(found.getItems().get(0).bookTitle()).isEqualTo("Clean Code");
        assertThat(found.getItems().get(0).quantity()).isEqualTo(2);
    }
}
