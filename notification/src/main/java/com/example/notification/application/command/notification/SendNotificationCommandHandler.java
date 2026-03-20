package com.example.notification.application.command.notification;

import com.example.notification.application.port.outbound.CustomerClient;
import com.example.notification.application.port.outbound.EmailSender;
import com.example.notification.domain.ports.NotificationRepository;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.Payload;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendNotificationCommandHandler implements CommandHandler<SendNotificationCommand, Void> {

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;
    private final CustomerClient customerClient;

    @Override
    // No @Transactional here — transaction is on NotificationPersistenceAdapter.save()
    public Void handle(SendNotificationCommand command) {
        // Step 1: Resolve email (HTTP — outside transaction)
        String recipientEmail = customerClient.findEmail(command.customerId()).orElse(null);

        if (recipientEmail == null) {
            Notification notification = Notification.create(
                    command.customerId(), null, command.channel(),
                    new Payload(command.subject(), command.body()));
            notification.markFailed("no email address found for customerId=" + command.customerId());
            log.warn("Notification skipped — no email address for customerId={}", command.customerId());
            notificationRepository.save(notification);   // transaction 1 commits
            return null;
        }

        // Step 2: Create notification in PENDING state
        Notification notification = Notification.create(
                command.customerId(), recipientEmail,
                command.channel(), new Payload(command.subject(), command.body()));

        // Step 3: Save PENDING record first — DB commits here (transaction 1)
        notificationRepository.save(notification);

        // Step 4: Send email outside any transaction
        try {
            emailSender.send(recipientEmail, notification.getPayload());
            notification.markSent();
            log.info("Notification sent: customerId={}, subject={}", command.customerId(), command.subject());
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            notification.markFailed(reason);
            log.error("Notification failed: customerId={}, reason={}", command.customerId(), reason);
        }

        // Step 5: Save final status (SENT or FAILED) — DB commits here (transaction 2)
        notificationRepository.save(notification);
        return null;
    }
}
