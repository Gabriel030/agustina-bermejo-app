package com.red_escuela.backend.graduation.certificate.verification.usecase;

import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;

public interface VerifyPublicCertificateUseCase {

    PublicCertificateView verify(String code, String clientKey);
}
