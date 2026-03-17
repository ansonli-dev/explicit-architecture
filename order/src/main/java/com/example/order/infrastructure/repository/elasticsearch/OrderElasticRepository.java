package com.example.order.infrastructure.repository.elasticsearch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

interface OrderElasticRepository extends ElasticsearchRepository<OrderElasticDocument, String> {
    Page<OrderElasticDocument> findByCustomerIdAndStatus(String customerId, String status, Pageable pageable);

    Page<OrderElasticDocument> findByCustomerId(String customerId, Pageable pageable);
}
