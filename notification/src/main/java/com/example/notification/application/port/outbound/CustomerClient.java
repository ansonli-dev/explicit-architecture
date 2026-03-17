package com.example.notification.application.port.outbound;

import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for resolving customer contact information.
 * In production this would call a dedicated customer/identity service.
 */
public interface CustomerClient {
    Optional<String> findEmail(UUID customerId);
}
