package com.red_escuela.backend.notification.broadcast.lifecycle.specialist;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastNotFoundException;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import com.red_escuela.backend.notification.broadcast.freezer.specialist.BroadcastTotalsFreezer;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastProgressTracker;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class BroadcastCanceller {

    static final String DISCARD_REASON = "BROADCAST_CANCELLED";

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final BroadcastTotalsFreezer totalsFreezer;
    private final BroadcastProgressTracker progressTracker;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public BroadcastCanceller(BroadcastRepository broadcastRepository, BroadcastRecipientRepository recipientRepository,
                              NotificationDeliveryRepository deliveryRepository, BroadcastTotalsFreezer totalsFreezer, BroadcastProgressTracker progressTracker,
                              AuditTrail auditTrail, Clock clock) {
        this.broadcastRepository = broadcastRepository;
        this.recipientRepository = recipientRepository;
        this.deliveryRepository = deliveryRepository;
        this.totalsFreezer = totalsFreezer;
        this.progressTracker = progressTracker;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public BroadcastStatus cancelBroadcast(Long broadcastId, Long cancelledByPersonId, String reason) {
        Broadcast broadcast = broadcastRepository.findByIdForUpdate(broadcastId).orElseThrow(() -> new BroadcastNotFoundException(broadcastId));
        Instant now = clock.instant();
        BroadcastStatus before = broadcast.getStatus();
        broadcast.cancel(cancelledByPersonId, reason.strip(), now);
        List<BroadcastRecipient> open = recipientRepository.findByBroadcastIdAndStatusIn(broadcastId,
                List.of(BroadcastRecipientStatus.PENDING, BroadcastRecipientStatus.QUEUED));
        open.forEach(recipient -> settleOpenRecipient(recipient, now));
        totalsFreezer.freezeTotals(broadcast);
        auditTrail.record(new AuditRecord(AuditAction.BROADCAST_CANCELLED, AuditEntityType.BROADCAST, broadcastId,
                before.name(), broadcast.getStatus().name(), broadcast.getCancellationReason()));
        return broadcast.getStatus();
    }

    private void settleOpenRecipient(BroadcastRecipient recipient, Instant now) {
        Optional<NotificationDelivery> delivery = recipient.getDeliveryId() == null
                ? Optional.empty()
                : deliveryRepository.findByIdForUpdate(recipient.getDeliveryId());
        if (delivery.isEmpty()) {
            recipient.markSkipped(now);
            return;
        }
        if (delivery.get().getStatus().isWaitingForDispatch()) {
            delivery.get().discard(DISCARD_REASON);
            recipient.markSkipped(now);
            return;
        }
        progressTracker.applyDeliveryOutcome(recipient, delivery.get(), now);
    }
}
