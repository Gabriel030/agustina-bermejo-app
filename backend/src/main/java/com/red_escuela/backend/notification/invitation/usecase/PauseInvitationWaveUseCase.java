package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;

public interface PauseInvitationWaveUseCase {

    InvitationWaveResponse pauseInvitationWave(Long waveId, Long actorPersonId);
}
