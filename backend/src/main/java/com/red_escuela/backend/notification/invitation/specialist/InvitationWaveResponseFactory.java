package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveRecipientEntry;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import org.springframework.stereotype.Component;

@Component
public class InvitationWaveResponseFactory {

    private final InvitationWaveRecipientRepository recipientRepository;

    public InvitationWaveResponseFactory(InvitationWaveRecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    public InvitationWaveResponse createWaveResponse(InvitationWave wave) {
        long eligibleCount = recipientRepository.countByWaveIdAndStatus(wave.getId(), InvitationRecipientStatus.PENDING);
        return new InvitationWaveResponse(wave.getId(), wave.getName(), wave.getStatus(), wave.getBatchSize(),
                wave.getIntervalMinutes(), wave.getDailyCap(), wave.getImportBatchId(), wave.getCreatedByPersonId(),
                wave.getCreatedAt(), wave.getStartedAt(), wave.getPausedAt(), wave.getCompletedAt(),
                wave.getCancelledAt(), eligibleCount, wave.getSentTotal(), wave.getFailedTotal(),
                wave.getExcludedTotal(), wave.getActivatedTotal());
    }

    public InvitationWaveRecipientEntry createRecipientEntry(InvitationWaveRecipient recipient) {
        return new InvitationWaveRecipientEntry(recipient.getPersonId(), recipient.getStatus(),
                recipient.getExclusionReason(), recipient.getAttempts(), recipient.getLastError(), recipient.getSentAt());
    }
}
