package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalDocumentsResponse;
import com.red_escuela.backend.identity.specialist.LegalDocumentCatalog;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCurrentLegalDocumentsService implements GetCurrentLegalDocumentsUseCase {

    private final LegalDocumentCatalog legalDocumentCatalog;

    public GetCurrentLegalDocumentsService(LegalDocumentCatalog legalDocumentCatalog) {
        this.legalDocumentCatalog = legalDocumentCatalog;
    }

    @Override
    public LegalDocumentsResponse getCurrentDocuments() {
        return new LegalDocumentsResponse(legalDocumentCatalog.current());
    }
}
