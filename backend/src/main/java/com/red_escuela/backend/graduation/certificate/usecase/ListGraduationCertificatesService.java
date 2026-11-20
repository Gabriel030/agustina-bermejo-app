package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateFilter;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateSummaryResponse;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificateCatalog;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@UseCase
public class ListGraduationCertificatesService implements ListGraduationCertificatesUseCase {

    private final GraduationCertificateCatalog catalog;

    public ListGraduationCertificatesService(GraduationCertificateCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public Page<GraduationCertificateSummaryResponse> list(GraduationCertificateFilter filter, Pageable pageable) {
        return catalog.list(filter, pageable);
    }
}
