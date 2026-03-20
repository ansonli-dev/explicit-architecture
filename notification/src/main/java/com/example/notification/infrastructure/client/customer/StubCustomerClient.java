package com.example.notification.infrastructure.client.customer;

import com.example.notification.application.port.outbound.CustomerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Stub implementation of CustomerClient.
 * Returns a deterministic placeholder email derived from the customerId.
 * <p>
 * TODO: Replace with a real HTTP/gRPC call to the customer service when available.
 */
@Component
@ConditionalOnMissingBean(CustomerClient.class)
public class StubCustomerClient implements CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(StubCustomerClient.class);

    @Override
    public Optional<String> findEmail(UUID customerId) {
        // Stub: derive a stable placeholder email from the customerId
        String email = "customer." + customerId + "@example.com";
        log.debug("[STUB] Resolved email for customerId={} -> {}", customerId, email);
        return Optional.of(email);
    }
}
