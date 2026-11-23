package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveLifecycleTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ResumeInvitationWaveService implements ResumeInvitationWaveUseCase {

    private final InvitationWaveLifecycleTransaction lifecycleTransaction;

    public ResumeInvitationWaveService(InvitationWaveLifecycleTransaction lifecycleTransaction) {
        this.lifecycleTransaction = lifecycleTransaction;
    }

    @Override
    public InvitationWaveResponse resumeInvitationWave(Long waveId, Long actorPersonId) {
        return lifecycleTransaction.resumeInvitationWave(waveId, actorPersonId);
    }
}
