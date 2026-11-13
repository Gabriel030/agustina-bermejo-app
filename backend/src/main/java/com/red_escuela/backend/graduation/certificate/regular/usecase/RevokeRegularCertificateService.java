package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateReader;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateRevoker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeRegularCertificateService implements RevokeRegularCertificateUseCase {

    private final RegularCertificateRevoker revoker;
    private final RegularCertificateReader reader;

    public RevokeRegularCertificateService(RegularCertificateRevoker revoker, RegularCertificateReader reader) {
        this.revoker = revoker;
        this.reader = reader;
    }

    @Override
    public RegularCertificateResponse revoke(Long certificateId, String reason) {
        revoker.revoke(certificateId, reason);
        return reader.get(certificateId);
    }
}
