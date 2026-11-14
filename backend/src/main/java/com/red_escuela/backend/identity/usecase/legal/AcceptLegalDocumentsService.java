package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.dto.PendingLegalAcceptancesResponse;
import com.red_escuela.backend.identity.specialist.LegalAcceptanceRecorder;
import com.red_escuela.backend.identity.specialist.LegalPendingFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class AcceptLegalDocumentsService implements AcceptLegalDocumentsUseCase {

    private final LegalAcceptanceRecorder legalAcceptanceRecorder;
    private final LegalPendingFinder legalPendingFinder;

    public AcceptLegalDocumentsService(LegalAcceptanceRecorder legalAcceptanceRecorder, LegalPendingFinder legalPendingFinder) {
        this.legalAcceptanceRecorder = legalAcceptanceRecorder;
        this.legalPendingFinder = legalPendingFinder;
    }

    @Override
    public PendingLegalAcceptancesResponse acceptCurrentDocuments(LegalAcceptanceEvidence evidence) {
        legalAcceptanceRecorder.acceptForCurrentPerson(evidence);
        return legalPendingFinder.pendingForCurrentPerson();
    }
}
