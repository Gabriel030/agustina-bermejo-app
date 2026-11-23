package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListInvitationWavesService implements ListInvitationWavesUseCase {

    private final InvitationWaveReader reader;

    public ListInvitationWavesService(InvitationWaveReader reader) {
        this.reader = reader;
    }

    @Override
    public PageResponse<InvitationWaveResponse> listInvitationWaves(int page, int size) {
        return reader.readInvitationWaves(page, size);
    }
}
