package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.dataimport.api.ImportedAccountsQuery;
import com.red_escuela.backend.identity.api.PersonContactSummary;
import com.red_escuela.backend.notification.invitation.domain.InvitationExclusionReason;
import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveRecipientEntry;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

@Component
public class InvitationWaveResponseFactory {

    private final InvitationWaveRecipientRepository recipientRepository;

    private final ImportedAccountsQuery importedAccountsQuery;

    public InvitationWaveResponseFactory(InvitationWaveRecipientRepository recipientRepository,
                                         ImportedAccountsQuery importedAccountsQuery) {
        this.recipientRepository = recipientRepository;
        this.importedAccountsQuery = importedAccountsQuery;
    }

    public InvitationWaveResponse createWaveResponse(InvitationWave wave) {
        Map<InvitationRecipientStatus, Long> totals = new EnumMap<>(InvitationRecipientStatus.class);
        recipientRepository.countByStatusForWave(wave.getId()).forEach(count -> totals.put(count.getStatus(), count.getTotal()));
        Map<InvitationExclusionReason, Long> excludedByReason = new EnumMap<>(InvitationExclusionReason.class);
        for (InvitationExclusionReason reason : InvitationExclusionReason.values()) {
            excludedByReason.put(reason, 0L);
        }
        recipientRepository.countExcludedByReasonForWave(wave.getId())
                .forEach(count -> excludedByReason.put(count.getReason(), count.getTotal()));
        long recipientsTotal = totals.values().stream().mapToLong(Long::longValue).sum();
        long excludedTotal = totals.getOrDefault(InvitationRecipientStatus.EXCLUDED, 0L);
        return new InvitationWaveResponse(wave.getId(), wave.getName(), wave.getStatus(), wave.getBatchSize(),
                wave.getIntervalMinutes(), wave.getDailyCap(), wave.getImportBatchId(), wave.getCreatedByPersonId(),
                wave.getCreatedAt(), wave.getStartedAt(), wave.getStartedByPersonId(), wave.getPausedAt(),
                wave.getPausedByPersonId(), wave.getResumedAt(), wave.getResumedByPersonId(), wave.getCompletedAt(), wave.getCancelledAt(), wave.getCancelledByPersonId(),
                totals.getOrDefault(InvitationRecipientStatus.PENDING, 0L),
                totals.getOrDefault(InvitationRecipientStatus.QUEUED, 0L),
                totals.getOrDefault(InvitationRecipientStatus.SENT, 0L),
                totals.getOrDefault(InvitationRecipientStatus.FAILED, 0L),
                totals.getOrDefault(InvitationRecipientStatus.EXCLUDED, 0L),
                totals.getOrDefault(InvitationRecipientStatus.ACTIVATED, 0L),
                importedAccountsQuery.findImportBatchName(wave.getImportBatchId()).orElse(null),
                recipientsTotal - excludedTotal, excludedByReason, null);
    }

    public InvitationWaveRecipientEntry createRecipientEntry(InvitationWaveRecipient recipient, Instant deliveryCreatedAt,
                                                             PersonContactSummary contact) {
        return new InvitationWaveRecipientEntry(recipient.getPersonId(), recipient.getStatus(),
                recipient.getExclusionReason(), recipient.getAttempts(), recipient.getLastErrorCode(), recipient.getSentAt(),
                latestOf(recipient.getSentAt(), deliveryCreatedAt),
                contact == null ? null : contact.displayName(), contact == null ? null : contact.email());
    }

    private Instant latestOf(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isAfter(second) ? first : second;
    }
}
