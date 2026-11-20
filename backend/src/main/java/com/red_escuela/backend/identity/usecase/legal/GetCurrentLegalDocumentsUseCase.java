package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalDocumentsResponse;

public interface GetCurrentLegalDocumentsUseCase {
    LegalDocumentsResponse getCurrentDocuments();
}
