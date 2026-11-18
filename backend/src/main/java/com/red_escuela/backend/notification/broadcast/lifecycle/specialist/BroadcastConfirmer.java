package com.red_escuela.backend.notification.broadcast.lifecycle.specialist;

import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.notification.broadcast.domain.DispatchOutcome;
import com.red_escuela.backend.notification.broadcast.domain.OutboundMessage;
import com.red_escuela.backend.notification.broadcast.domain.RenderedContent;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.exception.BroadcastNotFoundException;
import com.red_escuela.backend.notification.broadcast.draft.exception.EmptyAudienceException;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.entity.BroadcastRecipient;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import com.red_escuela.backend.notification.broadcast.freezer.specialist.RecipientFreezer;
import com.red_escuela.backend.notification.broadcast.specialist.ChannelRegistry;
import com.red_escuela.backend.notification.broadcast.specialist.MessageChannel;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastProgressTracker;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class BroadcastConfirmer {

    private static final Set<BroadcastStatus> ALREADY_CONFIRMED = Set.of(BroadcastStatus.CONFIRMED, BroadcastStatus.SENDING, BroadcastStatus.COMPLETED);

    private final BroadcastRepository broadcastRepository;
    private final BroadcastRecipientRepository recipientRepository;
    private final RecipientFreezer recipientFreezer;
    private final ChannelRegistry channelRegistry;
    private final BroadcastProgressTracker progressTracker;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public BroadcastConfirmer(BroadcastRepository broadcastRepository, BroadcastRecipientRepository recipientRepository,
                              RecipientFreezer recipientFreezer, ChannelRegistry channelRegistry, BroadcastProgressTracker progressTracker, AuditTrail auditTrail, Clock clock) {
        this.broadcastRepository = broadcastRepository;
        this.recipientRepository = recipientRepository;
        this.recipientFreezer = recipientFreezer;
        this.channelRegistry = channelRegistry;
        this.progressTracker = progressTracker;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional
    public BroadcastStatus confirmBroadcast(Long broadcastId, Long confirmedByPersonId) {
        Broadcast broadcast = broadcastRepository.findByIdForUpdate(broadcastId).orElseThrow(() -> new BroadcastNotFoundException(broadcastId));
        if (ALREADY_CONFIRMED.contains(broadcast.getStatus())) {
            return broadcast.getStatus();
        }
        Instant now = clock.instant();
        BroadcastStatus before = broadcast.getStatus();
        broadcast.confirm(confirmedByPersonId, now);
        recipientFreezer.freezeRecipients(broadcastId);
        List<BroadcastRecipient> pending = recipientRepository.findByBroadcastIdAndStatusIn(broadcastId, List.of(BroadcastRecipientStatus.PENDING));
        if (pending.isEmpty()) {
            throw new EmptyAudienceException();
        }
        MessageChannel channel = channelRegistry.channel(broadcast.getChannel());
        RenderedContent content = new RenderedContent(broadcast.getSubjectSnapshot(), broadcast.getBodySnapshot());
        pending.forEach(recipient -> queueRecipient(channel, broadcast, content, recipient, now));
        auditTrail.record(new AuditRecord(AuditAction.BROADCAST_CONFIRMED, AuditEntityType.BROADCAST, broadcastId,
                before.name(), broadcast.getStatus().name(), null));
        progressTracker.completeWhenNoOpenRecipients(broadcast, now);
        return broadcast.getStatus();
    }

    private void queueRecipient(MessageChannel channel, Broadcast broadcast, RenderedContent content, BroadcastRecipient recipient, Instant now) {
        Optional<ChannelAddress> address = channel.resolveAddress(recipient.getPersonId());
        if (address.isEmpty()) {
            recipient.markSkipped(now);
            return;
        }
        DispatchOutcome outcome = channel.dispatch(new OutboundMessage(broadcast.getId(), recipient.getPersonId(), address.get(), content,
                broadcast.getCategory()));
        switch (outcome) {
            case DispatchOutcome.Queued queued -> recipient.markQueued(queued.deliveryId(), now);
            case DispatchOutcome.Rejected rejected -> recipient.markSkipped(now);
            case DispatchOutcome.ReadyForManualAction manual -> recipient.markSkipped(now);
        }
    }
}
