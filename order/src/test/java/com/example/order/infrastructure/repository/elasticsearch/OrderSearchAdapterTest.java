package com.example.order.infrastructure.repository.elasticsearch;

import com.example.order.application.port.outbound.OrderSearchRepository;
import com.example.order.application.query.order.OrderDetailView;
import com.example.order.application.query.order.OrderSummaryView;
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

/**
 * Integration test for OrderSearchAdapter.
 *
 * Seeds documents directly via OrderElasticRepository using the same JSON string format
 * that the Elasticsearch Sink Connector produces after consuming Debezium CDC events.
 * Field names in the items JSON are snake_case (matching Debezium's jsonb serialization).
 */
@DataElasticsearchTest
@Testcontainers
@Import({OrderSearchAdapter.class})
class OrderSearchAdapterTest {

    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.4"))
            .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void elasticProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", elasticsearch::getHttpHostAddress);
    }

    @Autowired OrderElasticRepository elasticRepository;
    @Autowired OrderSearchRepository orderSearchRepository;

    private final UUID orderId    = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    /** Builds a document the way ES Sink Connector would write it after CDC. */
    private OrderElasticDocument buildCdcDoc(UUID ordId, UUID custId, String status) {
        var doc = new OrderElasticDocument();
        doc.setId(ordId.toString());
        doc.setCustomerId(custId.toString());
        doc.setCustomerEmail("user@example.com");
        doc.setStatus(status);
        doc.setTotalCents(9998L);
        doc.setCurrency("CNY");
        // items as a JSON string — this is exactly what Debezium sends for a jsonb column.
        // Field names are snake_case matching @JsonProperty annotations in ItemDocument.
        doc.setItems("[{\"id\":\"" + UUID.randomUUID() + "\",\"book_id\":\"" + UUID.randomUUID()
                + "\",\"book_title\":\"Clean Code\",\"unit_price_cents\":4999,\"currency\":\"CNY\",\"quantity\":2}]");
        return doc;
    }

    @Test
    void givenCdcDocument_whenFindById_thenOrderDetailViewReturned() {
        elasticRepository.save(buildCdcDoc(orderId, customerId, "PENDING"));

        Optional<OrderDetailView> found = orderSearchRepository.findById(orderId);

        assertThat(found).isPresent();
        assertThat(found.get().orderId()).isEqualTo(orderId);
        assertThat(found.get().customerId()).isEqualTo(customerId);
        assertThat(found.get().status()).isEqualTo("PENDING");
        assertThat(found.get().items()).hasSize(1);
        assertThat(found.get().items().get(0).bookTitle()).isEqualTo("Clean Code");
    }

    @Test
    void givenNonExistentId_whenFindById_thenReturnsEmpty() {
        assertThat(orderSearchRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void givenMultipleOrders_whenFindByCustomerIdAndStatus_thenMatchingOrdersReturned() {
        elasticRepository.save(buildCdcDoc(orderId, customerId, "PENDING"));
        elasticRepository.save(buildCdcDoc(UUID.randomUUID(), customerId, "CONFIRMED"));

        List<OrderSummaryView> pending = orderSearchRepository.findByCustomerIdAndStatus(customerId, "PENDING", 0, 20);
        List<OrderSummaryView> all     = orderSearchRepository.findByCustomerIdAndStatus(customerId, null, 0, 20);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).orderId()).isEqualTo(orderId);
        assertThat(all).hasSize(2);
    }
}
