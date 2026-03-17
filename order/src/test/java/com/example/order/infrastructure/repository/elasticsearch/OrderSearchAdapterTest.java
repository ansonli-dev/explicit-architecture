package com.example.order.infrastructure.repository.elasticsearch;

import com.example.order.application.port.outbound.OrderSearchRepository;
import com.example.order.application.port.outbound.OrderSearchRepository.OrderProjection;
import com.example.order.application.query.order.OrderDetailResponse;
import com.example.order.application.query.order.OrderItemResponse;
import com.example.order.application.query.order.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.elasticsearch.DataElasticsearchTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataElasticsearchTest
@Testcontainers
@Import(OrderSearchAdapter.class)
class OrderSearchAdapterTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.4"))
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void elasticProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
    }

    @Autowired OrderSearchRepository orderSearchRepository;

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private OrderProjection buildProjection(UUID ordId, UUID custId, String status) {
        var item = new OrderItemResponse(UUID.randomUUID(), "Clean Code", 4999L, "CNY", 2);
        return new OrderProjection(ordId, custId, "user@example.com", status, 9998L, "CNY", List.of(item));
    }

    @Test
    void givenOrderSaved_whenFindById_thenOrderDetailResponseReturned() {
        // Arrange
        orderSearchRepository.save(buildProjection(orderId, customerId, "PENDING"));

        // Act
        Optional<OrderDetailResponse> found = orderSearchRepository.findById(orderId);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().orderId()).isEqualTo(orderId);
        assertThat(found.get().customerId()).isEqualTo(customerId);
        assertThat(found.get().status()).isEqualTo("PENDING");
        assertThat(found.get().items()).hasSize(1);
    }

    @Test
    void givenNonExistentId_whenFindById_thenReturnsEmpty() {
        // Act
        Optional<OrderDetailResponse> found = orderSearchRepository.findById(UUID.randomUUID());

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    void givenOrdersForCustomer_whenFindByCustomerIdAndStatus_thenMatchingOrdersReturned() {
        // Arrange
        orderSearchRepository.save(buildProjection(orderId, customerId, "PENDING"));
        orderSearchRepository.save(buildProjection(UUID.randomUUID(), customerId, "CONFIRMED"));

        // Act
        List<OrderResponse> pendingOrders = orderSearchRepository.findByCustomerIdAndStatus(customerId, "PENDING", 0, 20);
        List<OrderResponse> allOrders = orderSearchRepository.findByCustomerIdAndStatus(customerId, null, 0, 20);

        // Assert
        assertThat(pendingOrders).hasSize(1);
        assertThat(pendingOrders.get(0).orderId()).isEqualTo(orderId);
        assertThat(allOrders).hasSize(2);
    }

    @Test
    void givenSavedOrder_whenUpdateStatus_thenStatusUpdated() {
        // Arrange
        orderSearchRepository.save(buildProjection(orderId, customerId, "PENDING"));

        // Act
        orderSearchRepository.updateStatus(orderId, "CONFIRMED");

        // Assert
        Optional<OrderDetailResponse> found = orderSearchRepository.findById(orderId);
        assertThat(found).isPresent();
        assertThat(found.get().status()).isEqualTo("CONFIRMED");
    }
}
