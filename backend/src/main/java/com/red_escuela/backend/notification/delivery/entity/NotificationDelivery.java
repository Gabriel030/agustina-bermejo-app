package com.red_escuela.backend.notification.delivery.entity;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEvent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatus;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatusReason;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatusStateMachine;
import com.red_escuela.backend.notification.delivery.exception.IllegalNotificationDeliveryStatusTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Getter
@Entity
@Table(name = "notification_delivery")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dedup_key", nullable = false, length = DeliveryEnvelope.MAX_DEDUP_KEY_LENGTH)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private DeliveryCategory category;

    @Column(name = "kind", nullable = false, length = DeliveryEnvelope.MAX_KIND_LENGTH)
    private String kind;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "recipient_person_id", nullable = false)
    private Long recipientPersonId;

    @Column(name = "related_entity_type", length = DeliveryEnvelope.MAX_RELATED_ENTITY_TYPE_LENGTH)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "status_reason", length = DeliveryStatusReason.MAX_LENGTH)
    private String statusReason;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDate scheduledFor;

    @Column(name = "not_before")
    private Instant notBefore;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "provider_message_id", length = 120)
    private String providerMessageId;

    @Column(name = "subject", length = DeliveryContent.MAX_SUBJECT_LENGTH)
    private String subject;

    @Column(name = "body_text", nullable = false, columnDefinition = "text")
    private String bodyText;

    @Column(name = "body_html", columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static NotificationDelivery queuedEmail(DeliveryEnvelope envelope, DeliveryContent content, Instant now) {
        return start(envelope, DeliveryChannel.EMAIL, content, now, DeliveryEvent.QUEUE_FOR_DISPATCH, null);
    }

    public static NotificationDelivery suppressedEmail(DeliveryEnvelope envelope, DeliveryContent content, String reason, Instant now) {
        return start(envelope, DeliveryChannel.EMAIL, content, now, DeliveryEvent.SUPPRESS, reason);
    }

    public static NotificationDelivery deliveredInApp(DeliveryEnvelope envelope, DeliveryContent content, Instant now) {
        NotificationDelivery delivery = start(envelope, DeliveryChannel.IN_APP, content, now, DeliveryEvent.DELIVER_IN_APP, null);
        delivery.sentAt = now;
        return delivery;
    }

    private static NotificationDelivery start(DeliveryEnvelope envelope, DeliveryChannel channel, DeliveryContent content, Instant now,
                                              DeliveryEvent initialEvent, String reason) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.dedupKey = envelope.dedupKey();
        delivery.channel = channel;
        delivery.category = envelope.category();
        delivery.kind = envelope.kind();
        delivery.priority = envelope.priority();
        delivery.recipientPersonId = envelope.recipientPersonId();
        delivery.relatedEntityType = envelope.relatedEntityType();
        delivery.relatedEntityId = envelope.relatedEntityId();
        delivery.scheduledFor = envelope.scheduledFor();
        delivery.subject = content.subject();
        delivery.bodyText = content.bodyText();
        delivery.bodyHtml = content.bodyHtml();
        delivery.createdAt = now;
        delivery.moveTo(initialEvent, reason);
        return delivery;
    }

    public void markSent(String providerMessageId, boolean simulated, Instant now) {
        moveTo(simulated ? DeliveryEvent.SEND_VIA_SIMULATOR : DeliveryEvent.SEND_VIA_PROVIDER, null);
        this.providerMessageId = providerMessageId;
        this.sentAt = now;
    }

    public void suppress(String reason) {
        moveTo(DeliveryEvent.SUPPRESS, reason);
    }

    public void defer(String reason, Instant notBefore) {
        Objects.requireNonNull(notBefore, "notBefore");
        moveTo(DeliveryEvent.DEFER, reason);
        this.notBefore = notBefore;
    }

    public void discard(String reason) {
        moveTo(DeliveryEvent.DISCARD, reason);
    }

    public void recordFailedAttempt(String reason, int maxAttempts) {
        if (!status.isWaitingForDispatch()) {
            throw new IllegalNotificationDeliveryStatusTransitionException(status, DeliveryEvent.GIVE_UP);
        }
        attempts++;
        if (attempts >= maxAttempts) {
            moveTo(DeliveryEvent.GIVE_UP, reason);
        } else {
            statusReason = DeliveryStatusReason.require(reason);
        }
    }

    private void moveTo(DeliveryEvent event, String reason) {
        DeliveryStatus target = DeliveryStatusStateMachine.findTargetStateFor(status, event)
                .orElseThrow(() -> new IllegalNotificationDeliveryStatusTransitionException(status, event));
        this.statusReason = target.requiresReason() ? DeliveryStatusReason.require(reason) : null;
        this.status = target;
    }
}
