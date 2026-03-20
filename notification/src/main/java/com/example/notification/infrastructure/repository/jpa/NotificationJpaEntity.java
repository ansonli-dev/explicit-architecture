package com.example.notification.infrastructure.repository.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.example.seedwork.infrastructure.jpa.AbstractAggregateRootEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationJpaEntity extends AbstractAggregateRootEntity {

    @Id
    private UUID id;

    @Version
    private Long version;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "delivery_status", nullable = false, length = 20)
    private String deliveryStatus;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
