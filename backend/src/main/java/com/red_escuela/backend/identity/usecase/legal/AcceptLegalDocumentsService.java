package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.dto.LegalDocumentsResponse;
import com.red_escuela.backend.identity.specialist.LegalAcceptanceRecorder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class AcceptLegalDocumentsService implements AcceptLegalDocumentsUseCase {

    private final LegalAcceptanceRecorder legalAcceptanceRecorder;

    public AcceptLegalDocumentsService(LegalAcceptanceRecorder legalAcceptanceRecorder) {
        this.legalAcceptanceRecorder = legalAcceptanceRecorder;
    }

    @Override
    public LegalDocumentsResponse acceptCurrentDocuments(LegalAcceptanceEvidence evidence) {
        return new LegalDocumentsResponse(legalAcceptanceRecorder.acceptForCurrentPerson(evidence));
    }
}
