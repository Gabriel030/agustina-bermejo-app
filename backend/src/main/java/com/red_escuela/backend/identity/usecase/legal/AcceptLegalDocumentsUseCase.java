package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.dto.PendingLegalAcceptancesResponse;

public interface AcceptLegalDocumentsUseCase {
    PendingLegalAcceptancesResponse acceptCurrentDocuments(LegalAcceptanceEvidence evidence);
}
