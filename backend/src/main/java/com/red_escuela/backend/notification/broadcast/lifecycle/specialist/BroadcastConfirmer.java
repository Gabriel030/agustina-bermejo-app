package com.red_escuela.backend.notification.broadcast.lifecycle.specialist;

import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
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
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeLinkFactory;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateRenderer;
import com.red_escuela.backend.notification.broadcast.template.domain.TemplateVariable;
import com.red_escuela.backend.notification.broadcast.template.exception.MissingRequiredVariableException;
import com.red_escuela.backend.notification.broadcast.template.specialist.TemplateVariableSource;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastProgressTracker;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private final TemplateVariableSource variableSource;
    private final UnsubscribeLinkFactory unsubscribeLinkFactory;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public BroadcastConfirmer(BroadcastRepository broadcastRepository, BroadcastRecipientRepository recipientRepository,
                              RecipientFreezer recipientFreezer, ChannelRegistry channelRegistry, BroadcastProgressTracker progressTracker,
                              TemplateVariableSource variableSource, UnsubscribeLinkFactory unsubscribeLinkFactory, AuditTrail auditTrail, Clock clock) {
        this.broadcastRepository = broadcastRepository;
        this.recipientRepository = recipientRepository;
        this.recipientFreezer = recipientFreezer;
        this.channelRegistry = channelRegistry;
        this.progressTracker = progressTracker;
        this.variableSource = variableSource;
        this.unsubscribeLinkFactory = unsubscribeLinkFactory;
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
        queueRecipients(channel, broadcast, pending, now);
        auditTrail.record(new AuditRecord(AuditAction.BROADCAST_CONFIRMED, AuditEntityType.BROADCAST, broadcastId,
                before.name(), broadcast.getStatus().name(), null));
        progressTracker.completeWhenNoOpenRecipients(broadcast, now);
        return broadcast.getStatus();
    }

    private void queueRecipients(MessageChannel channel, Broadcast broadcast, List<BroadcastRecipient> pending, Instant now) {
        Map<Long, Map<TemplateVariable, String>> valuesByPerson = variableSource.valuesForAll(pending.stream().map(BroadcastRecipient::getPersonId).toList());
        List<BroadcastRecipient> dispatchable = new ArrayList<>();
        List<OutboundMessage> messages = new ArrayList<>();
        for (BroadcastRecipient recipient : pending) {
            Optional<ChannelAddress> address = channel.resolveAddress(recipient.getPersonId());
            if (address.isEmpty()) {
                recipient.markSkipped(now, ExclusionReason.NO_CHANNEL_ADDRESS);
                continue;
            }
            Optional<RenderedContent> content = renderFor(channel.type(), broadcast, recipient.getPersonId(), valuesByPerson.get(recipient.getPersonId()));
            if (content.isEmpty()) {
                recipient.markSkipped(now, ExclusionReason.MISSING_TEMPLATE_VARIABLE);
                continue;
            }
            dispatchable.add(recipient);
            messages.add(new OutboundMessage(broadcast.getId(), recipient.getPersonId(), address.get(), content.get(), broadcast.getCategory()));
        }
        List<DispatchOutcome> outcomes = channel.dispatchAll(messages);
        for (int index = 0; index < dispatchable.size(); index++) {
            applyOutcome(dispatchable.get(index), outcomes.get(index), now);
        }
    }

    private Optional<RenderedContent> renderFor(ChannelType channelType, Broadcast broadcast, Long personId, Map<TemplateVariable, String> personValues) {
        if (personValues == null) {
            return Optional.empty();
        }
        Map<TemplateVariable, String> values = new EnumMap<>(TemplateVariable.class);
        values.putAll(personValues);
        values.put(TemplateVariable.UNSUBSCRIBE_LINK, unsubscribeLinkFactory.linkFor(personId, consentChannelOf(channelType), broadcast.getCategory()));
        try {
            return Optional.of(TemplateRenderer.render(channelType, broadcast.getSubjectSnapshot(), broadcast.getBodySnapshot(), values));
        } catch (MissingRequiredVariableException missing) {
            return Optional.empty();
        }
    }

    private static ConsentChannel consentChannelOf(ChannelType channelType) {
        return channelType == ChannelType.EMAIL ? ConsentChannel.EMAIL : ConsentChannel.WHATSAPP;
    }

    private void applyOutcome(BroadcastRecipient recipient, DispatchOutcome outcome, Instant now) {
        switch (outcome) {
            case DispatchOutcome.Queued queued -> recipient.markQueued(queued.deliveryId(), now);
            case DispatchOutcome.Rejected rejected -> recipient.markSkipped(now, exclusionReasonOf(rejected.reason()));
            case DispatchOutcome.ReadyForManualAction manual ->
                    throw new UnsupportedOperationException("Manual dispatch is not supported when confirming a broadcast");
        }
    }

    private static ExclusionReason exclusionReasonOf(DenialReason reason) {
        return switch (reason) {
            case OPTED_OUT -> ExclusionReason.OPTED_OUT;
            case NO_CONSENT -> ExclusionReason.NO_CONSENT;
        };
    }
}
