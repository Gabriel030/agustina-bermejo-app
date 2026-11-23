package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryChannel;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.domain.DeliveryStatusReason;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class InvitationDeliveryRecorder {

    public static final String RELATED_ENTITY_TYPE = "INVITATION_WAVE";
    private static final String KIND = "INVITATION_ACTIVATION";

    private final NotificationDeliveryRepository deliveryRepository;
    private final ActivationInvitationEmailFactory emailFactory;
    private final Clock clock;

    public InvitationDeliveryRecorder(NotificationDeliveryRepository deliveryRepository, ActivationInvitationEmailFactory emailFactory, Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.emailFactory = emailFactory;
        this.clock = clock;
    }

    public Optional<NotificationDelivery> recordQueuedAttempt(InvitationWaveRecipient recipient) {
        String dedupKey = "INVITATION:" + recipient.getWaveId() + ":" + recipient.getPersonId() + ":" + (recipient.getAttempts() + 1);
        if (deliveryRepository.existsByDedupKeyAndChannel(dedupKey, DeliveryChannel.EMAIL)) {
            return Optional.empty();
        }
        DeliveryEnvelope envelope = new DeliveryEnvelope(dedupKey, DeliveryCategory.INVITATION, KIND, DeliveryEnvelope.HIGHEST_PRIORITY,
                recipient.getPersonId(), RELATED_ENTITY_TYPE, recipient.getWaveId(), LocalDate.now(clock));
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(envelope, emailFactory.createAuditContent(), Instant.now(clock));
        return Optional.of(deliveryRepository.saveAndFlush(delivery));
    }

    public void discardQueuedDelivery(Long deliveryId) {
        if (deliveryId == null) {
            return;
        }
        deliveryRepository.findById(deliveryId)
                .filter(delivery -> delivery.getStatus().isWaitingForDispatch())
                .ifPresent(delivery -> delivery.discard(DeliveryStatusReason.NO_LONGER_APPLICABLE));
    }
}
