package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.notification.invitation.dto.InvitationImportBatchEntry;

import java.util.List;

public interface ListInvitationImportBatchesUseCase {
    List<InvitationImportBatchEntry> listInvitationImportBatches();
}
