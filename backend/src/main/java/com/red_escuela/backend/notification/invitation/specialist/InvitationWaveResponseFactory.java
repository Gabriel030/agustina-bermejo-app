package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveRecipientEntry;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class InvitationWaveResponseFactory {

    private final InvitationWaveRecipientRepository recipientRepository;

    public InvitationWaveResponseFactory(InvitationWaveRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    public InvitationWaveResponse createWaveResponse(InvitationWave wave) {
        Map<InvitationRecipientStatus, Long> totals = new EnumMap<>(InvitationRecipientStatus.class);
        recipientRepository.countByStatusForWave(wave.getId()).forEach(count -> totals.put(count.getStatus(), count.getTotal()));
        return new InvitationWaveResponse(wave.getId(), wave.getName(), wave.getStatus(), wave.getBatchSize(),
                wave.getIntervalMinutes(), wave.getDailyCap(), wave.getImportBatchId(), wave.getCreatedByPersonId(),
                wave.getCreatedAt(), wave.getStartedAt(), wave.getStartedByPersonId(), wave.getPausedAt(),
                wave.getPausedByPersonId(), wave.getResumedAt(), wave.getResumedByPersonId(), wave.getCompletedAt(), wave.getCancelledAt(), wave.getCancelledByPersonId(),
                totals.getOrDefault(InvitationRecipientStatus.PENDING, 0L),
                totals.getOrDefault(InvitationRecipientStatus.QUEUED, 0L),
                totals.getOrDefault(InvitationRecipientStatus.SENT, 0L),
                totals.getOrDefault(InvitationRecipientStatus.FAILED, 0L),
                totals.getOrDefault(InvitationRecipientStatus.EXCLUDED, 0L),
                totals.getOrDefault(InvitationRecipientStatus.ACTIVATED, 0L));
    }

    public InvitationWaveRecipientEntry createRecipientEntry(InvitationWaveRecipient recipient) {
        return new InvitationWaveRecipientEntry(recipient.getPersonId(), recipient.getStatus(),
                recipient.getExclusionReason(), recipient.getAttempts(), recipient.getLastErrorCode(), recipient.getSentAt());
    }
}
