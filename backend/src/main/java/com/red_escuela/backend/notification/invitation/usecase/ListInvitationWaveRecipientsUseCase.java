package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveRecipientEntry;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListInvitationWaveRecipientsUseCase {

    PageResponse<InvitationWaveRecipientEntry> listInvitationWaveRecipients(Long waveId, InvitationRecipientStatus status, int page, int size, boolean includePersonalData);
}
