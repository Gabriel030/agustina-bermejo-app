package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.entity.InvitationWave;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveNotFoundException;
import com.red_escuela.backend.notification.invitation.repository.InvitationWaveRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
public class InvitationWaveLifecycleTransaction {

    private final InvitationWaveRepository waveRepository;
    private final InvitationWaveResponseFactory responseFactory;
    private final Clock clock;

    public InvitationWaveLifecycleTransaction(InvitationWaveRepository waveRepository,
                                              InvitationWaveResponseFactory responseFactory, Clock clock) {
        this.waveRepository = waveRepository;
        this.responseFactory = responseFactory;
        this.clock = clock;
    }

    @Transactional
    public InvitationWaveResponse startInvitationWave(Long waveId) {
        InvitationWave wave = lockWave(waveId);
        wave.start(Instant.now(clock));
        return responseFactory.createWaveResponse(waveRepository.saveAndFlush(wave));
    }

    @Transactional
    public InvitationWaveResponse pauseInvitationWave(Long waveId) {
        InvitationWave wave = lockWave(waveId);
        wave.pause(Instant.now(clock));
        return responseFactory.createWaveResponse(waveRepository.saveAndFlush(wave));
    }

    @Transactional
    public InvitationWaveResponse resumeInvitationWave(Long waveId) {
        InvitationWave wave = lockWave(waveId);
        wave.resume();
        return responseFactory.createWaveResponse(waveRepository.saveAndFlush(wave));
    }

    @Transactional
    public InvitationWaveResponse cancelInvitationWave(Long waveId) {
        InvitationWave wave = lockWave(waveId);
        wave.cancel(Instant.now(clock));
        return responseFactory.createWaveResponse(waveRepository.saveAndFlush(wave));
    }

    private InvitationWave lockWave(Long waveId) {
        return waveRepository.findByIdForUpdate(waveId).orElseThrow(() -> new InvitationWaveNotFoundException(waveId));
    }
}
