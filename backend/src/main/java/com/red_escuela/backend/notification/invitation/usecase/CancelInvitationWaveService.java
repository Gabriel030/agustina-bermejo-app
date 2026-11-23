package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveLifecycleTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelInvitationWaveService implements CancelInvitationWaveUseCase {

    private final InvitationWaveLifecycleTransaction lifecycleTransaction;

    public CancelInvitationWaveService(InvitationWaveLifecycleTransaction lifecycleTransaction) {
        this.lifecycleTransaction = lifecycleTransaction;
    }

    @Override
    public InvitationWaveResponse cancelInvitationWave(Long waveId) {
        return lifecycleTransaction.cancelInvitationWave(waveId);
    }
}
