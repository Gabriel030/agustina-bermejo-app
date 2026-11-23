package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.domain.InvitationWaveStatus;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.entity.InvitationWaveRecipient;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveNotFoundException;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveNotRunningException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class InvitationFailedRetryTransaction {

    private final InvitationWaveRepository waveRepository;
    private final InvitationWaveRecipientRepository recipientRepository;
    private final InvitationDeliveryRecorder deliveryRecorder;
    private final InvitationSendingCapacity capacity;
    private final InvitationWaveSettings settings;

    public InvitationFailedRetryTransaction(InvitationWaveRepository waveRepository, InvitationWaveRecipientRepository recipientRepository,
                                            InvitationDeliveryRecorder deliveryRecorder, InvitationSendingCapacity capacity,
                                            InvitationWaveSettings settings) {
        this.waveRepository = waveRepository;
        this.recipientRepository = recipientRepository;
        this.deliveryRecorder = deliveryRecorder;
        this.capacity = capacity;
        this.settings = settings;
    }

    @Transactional
    public int retryFailedInvitations(Long waveId) {
        InvitationWave wave = waveRepository.findByIdForUpdate(waveId).orElseThrow(() -> new InvitationWaveNotFoundException(waveId));
        if (wave.getStatus() != InvitationWaveStatus.RUNNING) {
            throw new InvitationWaveNotRunningException(waveId);
        }
        long alreadyQueued = recipientRepository.countByWaveIdAndStatus(waveId, InvitationRecipientStatus.QUEUED);
        long room = capacity.remainingToday(wave) - alreadyQueued;
        if (room <= 0) {
            return 0;
        }
        List<InvitationWaveRecipient> retryable = recipientRepository.findByWaveIdAndStatusAndAttemptsLessThanOrderByIdAsc(
                waveId, InvitationRecipientStatus.FAILED, settings.maxAttempts(), PageRequest.of(0, (int) room));
        int requeued = 0;
        for (InvitationWaveRecipient recipient : retryable) {
            if (deliveryRecorder.recordQueuedAttempt(recipient).map(delivery -> {
                recipient.queue(delivery.getId());
                return true;
            }).orElse(false)) {
                requeued++;
            }
        }
        return requeued;
    }
}
