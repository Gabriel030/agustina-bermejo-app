package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveHasOpenRecipientsException;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveNotFoundException;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveWithoutRecipientsException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRecipientRepository;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
public class InvitationWaveLifecycleTransaction {

    private static final List<InvitationRecipientStatus> OPEN_STATUSES = List.of(InvitationRecipientStatus.PENDING, InvitationRecipientStatus.QUEUED);

    private final InvitationWaveRepository waveRepository;
    private final InvitationWaveRecipientRepository recipientRepository;
    private final InvitationWaveResponseFactory responseFactory;
    private final Clock clock;

    public InvitationWaveLifecycleTransaction(InvitationWaveRepository waveRepository,
                                              InvitationWaveRecipientRepository recipientRepository,
                                              InvitationWaveResponseFactory responseFactory, Clock clock) {
        this.waveRepository = waveRepository;
        this.recipientRepository = recipientRepository;
        this.responseFactory = responseFactory;
        this.clock = clock;
    }

    @Transactional
    public InvitationWaveResponse startInvitationWave(Long waveId, Long actorPersonId) {
        InvitationWave wave = lockWave(waveId);
        wave.start(Instant.now(clock), actorPersonId);
        if (recipientRepository.countByWaveIdAndStatus(waveId, InvitationRecipientStatus.PENDING) == 0) {
            throw new InvitationWaveWithoutRecipientsException(waveId);
        }
        return responseFactory.createWaveResponse(waveRepository.saveAndFlush(wave));
    }

    @Transactional
    public InvitationWaveResponse pauseInvitationWave(Long waveId, Long actorPersonId) {
        InvitationWave wave = lockWave(waveId);
        wave.pause(Instant.now(clock), actorPersonId);
        return responseFactory.createWaveResponse(waveRepository.saveAndFlush(wave));
    }

    @Transactional
    public InvitationWaveResponse resumeInvitationWave(Long waveId) {
        InvitationWave wave = lockWave(waveId);
        wave.resume();
        return responseFactory.createWaveResponse(waveRepository.saveAndFlush(wave));
    }

    @Transactional
    public InvitationWaveResponse cancelInvitationWave(Long waveId, Long actorPersonId) {
        InvitationWave wave = lockWave(waveId);
        wave.cancel(Instant.now(clock), actorPersonId);
        return responseFactory.createWaveResponse(waveRepository.saveAndFlush(wave));
    }

    @Transactional
    public InvitationWaveResponse completeInvitationWave(Long waveId) {
        InvitationWave wave = lockWave(waveId);
        wave.complete(Instant.now(clock));
        if (recipientRepository.countByWaveIdAndStatusIn(waveId, OPEN_STATUSES) > 0) {
            throw new InvitationWaveHasOpenRecipientsException(waveId);
        }
        return responseFactory.createWaveResponse(waveRepository.saveAndFlush(wave));
    }

    private InvitationWave lockWave(Long waveId) {
        return waveRepository.findByIdForUpdate(waveId).orElseThrow(() -> new InvitationWaveNotFoundException(waveId));
    }
}
