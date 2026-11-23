package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.domain.InvitationRecipientStatus;
import com.red_escuela.backend.notification.invitation.dto.InvitationWaveRecipientEntry;
import com.red_escuela.backend.notification.invitation.specialist.InvitationWaveReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListInvitationWaveRecipientsService implements ListInvitationWaveRecipientsUseCase {

    private final InvitationWaveReader reader;

    public ListInvitationWaveRecipientsService(InvitationWaveReader reader) {
        this.reader = reader;
    }

    @Override
    public PageResponse<InvitationWaveRecipientEntry> listInvitationWaveRecipients(Long waveId, InvitationRecipientStatus status, int page, int size, boolean includePersonalData) {
        return reader.readInvitationWaveRecipients(waveId, status, page, size, includePersonalData);
    }
}
