package com.example.notification.application.port.outbound;

import com.example.notification.domain.model.Payload;

/**
 * Secondary port for sending emails.
 * Implemented by LogEmailAdapter (simulation) in infrastructure/email/.
 */
public interface EmailSender {
    void send(String to, Payload payload);
}
