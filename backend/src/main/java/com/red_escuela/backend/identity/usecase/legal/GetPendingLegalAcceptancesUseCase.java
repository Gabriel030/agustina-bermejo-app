package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.PendingLegalAcceptancesResponse;

public interface GetPendingLegalAcceptancesUseCase {
    PendingLegalAcceptancesResponse getPendingDocuments();
}
