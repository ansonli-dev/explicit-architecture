package com.example.notification.infrastructure.client.email;

import com.example.notification.application.port.outbound.EmailSender;
import com.example.notification.domain.model.Payload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Secondary adapter implementing EmailSender port.
 * Active when notification.email.log-only=true (default / demo mode).
 * Replace with an SmtpEmailAdapter bound to notification.email.log-only=false for production.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.email.log-only", havingValue = "true", matchIfMissing = true)
public class LogEmailAdapter implements EmailSender {

    @Override
    public void send(String to, Payload payload) {
        log.info("📧 [EMAIL SIMULATION] TO: {} | SUBJECT: {} | BODY: {}",
                maskEmail(to), payload.subject(), payload.body());
    }

    private static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return "***" + domain;
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
