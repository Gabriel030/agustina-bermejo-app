package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalDocumentsResponse;
import com.red_escuela.backend.identity.specialist.LegalAcceptanceRecorder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetPendingLegalAcceptancesService implements GetPendingLegalAcceptancesUseCase {

    private final LegalAcceptanceRecorder legalAcceptanceRecorder;

    public GetPendingLegalAcceptancesService(LegalAcceptanceRecorder legalAcceptanceRecorder) {
        this.legalAcceptanceRecorder = legalAcceptanceRecorder;
    }

    @Override
    public LegalDocumentsResponse getPendingDocuments() {
        return new LegalDocumentsResponse(legalAcceptanceRecorder.pendingForCurrentPerson());
    }
}
