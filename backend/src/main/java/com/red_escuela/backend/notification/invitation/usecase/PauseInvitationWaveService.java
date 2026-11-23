package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveLifecycleTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PauseInvitationWaveService implements PauseInvitationWaveUseCase {

    private final InvitationWaveLifecycleTransaction lifecycleTransaction;

    public PauseInvitationWaveService(InvitationWaveLifecycleTransaction lifecycleTransaction) {
        this.lifecycleTransaction = lifecycleTransaction;
    }

    @Override
    public InvitationWaveResponse pauseInvitationWave(Long waveId) {
        return lifecycleTransaction.pauseInvitationWave(waveId);
    }
}
