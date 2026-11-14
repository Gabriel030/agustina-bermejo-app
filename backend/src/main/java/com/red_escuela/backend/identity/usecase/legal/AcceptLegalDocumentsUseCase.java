package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.dto.LegalDocumentsResponse;

public interface AcceptLegalDocumentsUseCase {
    LegalDocumentsResponse acceptCurrentDocuments(LegalAcceptanceEvidence evidence);
}
