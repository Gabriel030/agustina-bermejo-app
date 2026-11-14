package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.PendingLegalAcceptancesResponse;
import com.red_escuela.backend.identity.specialist.LegalPendingFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetPendingLegalAcceptancesService implements GetPendingLegalAcceptancesUseCase {

    private final LegalPendingFinder legalPendingFinder;

    public GetPendingLegalAcceptancesService(LegalPendingFinder legalPendingFinder) {
        this.legalPendingFinder = legalPendingFinder;
    }

    @Override
    public PendingLegalAcceptancesResponse getPendingDocuments() {
        return legalPendingFinder.pendingForCurrentPerson();
    }
}
