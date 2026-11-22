package com.red_escuela.backend.graduation.certificate.verification.usecase;

import com.red_escuela.backend.graduation.certificate.verification.dto.PublicDocumentVerificationResponse;

public interface VerifyPublicDocumentUseCase {

    PublicDocumentVerificationResponse verify(String code, String clientAddress);
}
