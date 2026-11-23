package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.CreateInvitationWaveCommand;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;

public interface CreateInvitationWaveUseCase {

    InvitationWaveResponse createInvitationWave(CreateInvitationWaveCommand command);
}
