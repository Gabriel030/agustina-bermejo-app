package com.red_escuela.backend.graduation.certificate.verification.usecase;

import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;
import com.red_escuela.backend.graduation.certificate.verification.specialist.PublicCertificateLookup;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class VerifyPublicCertificateService implements VerifyPublicCertificateUseCase {

    private final PublicCertificateLookup lookup;

    public VerifyPublicCertificateService(PublicCertificateLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public PublicCertificateView verify(String code, String clientKey) {
        return lookup.verify(code, clientKey);
    }
}
