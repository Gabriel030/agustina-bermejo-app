package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetInvitationWaveService implements GetInvitationWaveUseCase {

    private final InvitationWaveReader reader;

    public GetInvitationWaveService(InvitationWaveReader reader) {
        this.reader = reader;
    }

    @Override
    public InvitationWaveResponse getInvitationWave(Long waveId) {
        return reader.readInvitationWave(waveId);
    }
}
