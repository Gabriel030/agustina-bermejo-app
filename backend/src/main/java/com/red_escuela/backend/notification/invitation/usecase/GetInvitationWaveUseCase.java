package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;

public interface GetInvitationWaveUseCase {

    InvitationWaveResponse getInvitationWave(Long waveId);
}
