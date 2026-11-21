package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificateCatalog;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificateReissuer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReissueGraduationCertificateService implements ReissueGraduationCertificateUseCase {

    private final GraduationCertificateReissuer reissuer;
    private final GraduationCertificateCatalog catalog;

    public ReissueGraduationCertificateService(GraduationCertificateReissuer reissuer, GraduationCertificateCatalog catalog) {
        this.reissuer = reissuer;
        this.catalog = catalog;
    }

    @Override
    public GraduationCertificateDetailResponse reissue(Long id, String reason) {
        return catalog.get(reissuer.reissue(id, reason));
    }
}
