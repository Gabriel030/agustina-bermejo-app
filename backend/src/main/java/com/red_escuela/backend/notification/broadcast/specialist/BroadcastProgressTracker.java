package com.red_escuela.backend.notification.broadcast.specialist;

import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import com.red_escuela.backend.notification.broadcast.freezer.specialist.BroadcastTotalsFreezer;
import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class BroadcastProgressTracker {

    private static final Set<BroadcastStatus> IN_PROGRESS = Set.of(BroadcastStatus.CONFIRMED, BroadcastStatus.SENDING);
    private static final List<BroadcastRecipientStatus> OPEN = List.of(BroadcastRecipientStatus.PENDING, BroadcastRecipientStatus.QUEUED);

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final BroadcastTotalsFreezer totalsFreezer;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public BroadcastProgressTracker(BroadcastRepository broadcastRepository, BroadcastRecipientRepository recipientRepository,
                                    NotificationDeliveryRepository deliveryRepository, BroadcastTotalsFreezer totalsFreezer,
                                    AuditTrail auditTrail, Clock clock) {
        this.broadcastRepository = broadcastRepository;
        this.recipientRepository = recipientRepository;
        this.deliveryRepository = deliveryRepository;
        this.totalsFreezer = totalsFreezer;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Long> findResolvedQueuedDeliveryIds(int limit) {
        return recipientRepository.findQueuedDeliveryIdsAlreadyResolved(PageRequest.of(0, limit));
    }

    @Transactional
    public void syncDelivery(Long deliveryId) {
        Optional<BroadcastRecipient> found = recipientRepository.findByDeliveryId(deliveryId);
        if (found.isEmpty()) {
            return;
        }
        Broadcast broadcast = broadcastRepository.findByIdForUpdate(found.get().getBroadcastId()).orElseThrow();
        if (!IN_PROGRESS.contains(broadcast.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        if (broadcast.getStatus() == BroadcastStatus.CONFIRMED) {
            broadcast.startSending();
        }
        BroadcastRecipient recipient = found.get();
        if (recipient.isAwaitingDelivery()) {
            deliveryRepository.findById(deliveryId).ifPresent(delivery -> applyDeliveryOutcome(recipient, delivery, now));
        }
        completeWhenNoOpenRecipients(broadcast, now);
    }

    public void completeWhenNoOpenRecipients(Broadcast broadcast, Instant now) {
        if (recipientRepository.countByBroadcastIdAndStatusIn(broadcast.getId(), OPEN) == 0) {
            if (broadcast.getStatus() == BroadcastStatus.CONFIRMED) {
                broadcast.startSending();
            }
            completeBroadcast(broadcast, now);
        }
    }

    public void applyDeliveryOutcome(BroadcastRecipient recipient, NotificationDelivery delivery, Instant now) {
        switch (delivery.getStatus()) {
            case SENT, SENT_SIMULATED -> recipient.markSent(now);
            case FAILED -> recipient.markFailed(now);
            case SUPPRESSED, DISCARDED -> recipient.markSkipped(now);
            case QUEUED, DEFERRED -> {
            }
        }
    }

    private void completeBroadcast(Broadcast broadcast, Instant now) {
        BroadcastStatus before = broadcast.getStatus();
        broadcast.complete(now);
        totalsFreezer.freezeTotals(broadcast);
        auditTrail.record(new AuditRecord(AuditAction.BROADCAST_COMPLETED, AuditEntityType.BROADCAST, broadcast.getId(),
                before.name(), broadcast.getStatus().name(), null));
    }
}
