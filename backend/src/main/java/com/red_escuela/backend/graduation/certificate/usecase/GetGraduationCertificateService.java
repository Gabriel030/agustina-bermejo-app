package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificateCatalog;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetGraduationCertificateService implements GetGraduationCertificateUseCase {

    private final GraduationCertificateCatalog catalog;

    public GetGraduationCertificateService(GraduationCertificateCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public GraduationCertificateDetailResponse get(Long id) {
        return catalog.get(id);
    }
}
