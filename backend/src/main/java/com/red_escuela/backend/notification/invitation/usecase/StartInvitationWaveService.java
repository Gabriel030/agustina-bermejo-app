package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveLifecycleTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class StartInvitationWaveService implements StartInvitationWaveUseCase {

    private final InvitationWaveLifecycleTransaction lifecycleTransaction;

    public StartInvitationWaveService(InvitationWaveLifecycleTransaction lifecycleTransaction) {
        this.lifecycleTransaction = lifecycleTransaction;
    }

    @Override
    public InvitationWaveResponse startInvitationWave(Long waveId) {
        return lifecycleTransaction.startInvitationWave(waveId);
    }
}
