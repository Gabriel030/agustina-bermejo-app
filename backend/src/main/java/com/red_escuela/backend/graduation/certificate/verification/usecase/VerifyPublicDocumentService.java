package com.red_escuela.backend.graduation.certificate.verification.usecase;

import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import com.red_escuela.backend.graduation.certificate.verification.specialist.PublicDocumentLookup;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class VerifyPublicDocumentService implements VerifyPublicDocumentUseCase {

    private final PublicDocumentLookup lookup;

    public VerifyPublicDocumentService(PublicDocumentLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public PublicDocumentVerificationResponse verify(String code, String clientAddress) {
        return lookup.verify(code, clientAddress);
    }
}
