package com.red_escuela.backend.notification.invitation.specialist;

import com.red_escuela.backend.dataimport.api.ImportedAccount;
import com.red_escuela.backend.dataimport.api.ImportedAccountsPage;
import com.red_escuela.backend.dataimport.api.ImportedAccountsQuery;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveImportBatchNotFoundException;
import com.red_escuela.backend.notification.invitation.exception.InvitationWaveTooManyRecipientsException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class InvitationWaveCandidateScanner {

    private static final int PAGE_SIZE = 500;

    private final ImportedAccountsQuery importedAccountsQuery;
    private final InvitationWaveSettings settings;

    public InvitationWaveCandidateScanner(ImportedAccountsQuery importedAccountsQuery, InvitationWaveSettings settings) {
        this.importedAccountsQuery = importedAccountsQuery;
        this.settings = settings;
    }

    public List<ImportedAccount> findCandidates(Long importBatchId) {
        if (!importedAccountsQuery.importBatchExists(importBatchId)) {
            throw new InvitationWaveImportBatchNotFoundException(importBatchId);
        }
        Map<Long, ImportedAccount> byPersonId = new LinkedHashMap<>();
        Long cursor = null;
        do {
            ImportedAccountsPage page = importedAccountsQuery.findPendingActivation(importBatchId, cursor, PAGE_SIZE);
            for (ImportedAccount account : page.accounts()) {
                byPersonId.putIfAbsent(account.personId(), account);
            }
            if (byPersonId.size() > settings.maxRecipients()) {
                throw new InvitationWaveTooManyRecipientsException(settings.maxRecipients());
            }
            Long next = page.nextAfterPersonId();
            if (page.accounts().isEmpty() || next == null || (cursor != null && next <= cursor)) {
                break;
            }
            cursor = next;
        } while (true);
        return new ArrayList<>(byPersonId.values());
    }
}
