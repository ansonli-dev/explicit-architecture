package com.example.notification.application.command.notification;

import com.example.notification.application.port.outbound.CustomerClient;
import com.example.notification.application.port.outbound.EmailSender;
import com.example.notification.application.port.outbound.NotificationRepository;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.Payload;
import com.example.seedwork.application.command.CommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SendNotificationCommandHandler implements CommandHandler<SendNotificationCommand, Void> {

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;
    private final CustomerClient customerClient;

    @Override
    @Transactional
    public Void handle(SendNotificationCommand command) {
        String recipientEmail = customerClient.findEmail(command.customerId())
                .orElse(null);

        Notification notification = Notification.create(
                command.customerId(), recipientEmail,
                command.channel(), new Payload(command.subject(), command.body()));

        if (recipientEmail == null) {
            notification.markFailed("no email address found for customerId=" + command.customerId());
            log.warn("Notification skipped — no email address for customerId={}", command.customerId());
        } else {
            try {
                emailSender.send(recipientEmail, notification.getPayload());
                notification.markSent();
                log.info("Notification sent: customerId={}, subject={}", command.customerId(), command.subject());
            } catch (Exception e) {
                notification.markFailed(e.getMessage());
                log.error("Notification failed: customerId={}, reason={}", command.customerId(), e.getMessage());
            }
        }

        notificationRepository.save(notification);
        return null;
    }
}
