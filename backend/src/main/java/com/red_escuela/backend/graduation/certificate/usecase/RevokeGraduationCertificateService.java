package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificateCatalog;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificateRevoker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeGraduationCertificateService implements RevokeGraduationCertificateUseCase {

    private final GraduationCertificateRevoker revoker;
    private final GraduationCertificateCatalog catalog;

    public RevokeGraduationCertificateService(GraduationCertificateRevoker revoker, GraduationCertificateCatalog catalog) {
        this.revoker = revoker;
        this.catalog = catalog;
    }

    @Override
    public GraduationCertificateDetailResponse revoke(Long id, String reason) {
        revoker.revoke(id, reason);
        return catalog.get(id);
    }
}
