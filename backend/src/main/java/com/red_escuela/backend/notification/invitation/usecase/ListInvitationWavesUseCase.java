package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationWaveResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListInvitationWavesUseCase {

    PageResponse<InvitationWaveResponse> listInvitationWaves(int page, int size);
}
