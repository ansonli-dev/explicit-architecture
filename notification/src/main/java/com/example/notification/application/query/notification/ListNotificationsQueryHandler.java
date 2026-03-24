package com.example.notification.application.query.notification;

import com.example.notification.domain.ports.NotificationRepository;
import com.example.seedwork.application.query.QueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListNotificationsQueryHandler implements QueryHandler<ListNotificationsQuery, List<NotificationResult>> {

    private final NotificationRepository notificationRepository;

    @Override
    public List<NotificationResult> handle(ListNotificationsQuery query) {
        return notificationRepository.findByCustomerId(query.customerId(), query.page(), query.size())
                .stream()
                .map(n -> new NotificationResult(
                        n.getId().value(), n.getCustomerId(), n.getRecipientEmail(),
                        n.getChannel().name(), n.getPayload().subject(), n.getDeliveryStatus().name()))
                .toList();
    }
}
