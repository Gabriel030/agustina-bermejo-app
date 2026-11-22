package com.red_escuela.backend.graduation.certificate.verification.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class VerifyPublicDocumentService implements VerifyPublicDocumentUseCase {

    @Override
    public PublicDocumentVerificationResponse verify(String code, String clientAddress) {
        throw new DocumentOperationPendingException();
    }
}
