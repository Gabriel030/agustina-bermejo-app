package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.OwnGraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.specialist.OwnGraduationCertificateCatalog;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListOwnGraduationCertificatesService implements ListOwnGraduationCertificatesUseCase {

    private final OwnGraduationCertificateCatalog catalog;

    public ListOwnGraduationCertificatesService(OwnGraduationCertificateCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<OwnGraduationCertificateResponse> listOwn() {
        return catalog.list();
    }
}
