package com.example.notification.infrastructure.repository.jpa;

import com.example.notification.domain.ports.NotificationRepository;
import com.example.notification.domain.model.Channel;
import com.example.notification.domain.model.DeliveryStatus;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationId;
import com.example.notification.domain.model.Payload;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
class NotificationPersistenceAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    NotificationPersistenceAdapter(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public Notification save(Notification notification) {
        NotificationJpaEntity entity = toEntity(notification);
        entity.attachDomainEvents(notification.pullDomainEvents());
        jpaRepository.save(entity);
        return notification;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findByCustomerId(UUID customerId, int page, int size) {
        return jpaRepository.findByCustomerId(customerId, PageRequest.of(page, size))
                .stream().map(this::toDomain).toList();
    }

    private NotificationJpaEntity toEntity(Notification n) {
        NotificationJpaEntity e = new NotificationJpaEntity();
        e.setId(n.getId().value());
        e.setCustomerId(n.getCustomerId());
        e.setRecipientEmail(n.getRecipientEmail());
        e.setChannel(n.getChannel().name());
        e.setSubject(n.getPayload().subject());
        e.setBody(n.getPayload().body());
        e.setDeliveryStatus(n.getDeliveryStatus().name());
        e.setFailureReason(n.getFailureReason());
        return e;
    }

    private Notification toDomain(NotificationJpaEntity e) {
        return Notification.reconstitute(
                NotificationId.of(e.getId()), e.getCustomerId(), e.getRecipientEmail(),
                Channel.valueOf(e.getChannel()), new Payload(e.getSubject(), e.getBody()),
                DeliveryStatus.valueOf(e.getDeliveryStatus()), e.getFailureReason());
    }
}
