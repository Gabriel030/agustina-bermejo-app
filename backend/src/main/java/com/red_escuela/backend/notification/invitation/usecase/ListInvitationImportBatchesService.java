package com.red_escuela.backend.notification.invitation.usecase;

import com.red_escuela.backend.dataimport.api.ImportedAccountsQuery;
import com.red_escuela.backend.notification.invitation.dto.InvitationImportBatchEntry;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListInvitationImportBatchesService implements ListInvitationImportBatchesUseCase {

    private final ImportedAccountsQuery importedAccountsQuery;

    public ListInvitationImportBatchesService(ImportedAccountsQuery importedAccountsQuery) {
        this.importedAccountsQuery = importedAccountsQuery;
    }

    @Override
    public List<InvitationImportBatchEntry> listInvitationImportBatches() {
        return importedAccountsQuery.findImportedBatchesWithPendingActivation().stream()
                .map(batch -> new InvitationImportBatchEntry(batch.id(), batch.name(), batch.committedAt(),
                        batch.studentsPendingActivation()))
                .toList();
    }
}
