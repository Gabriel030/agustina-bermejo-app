package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateReader;
import com.red_escuela.backend.shared.UseCase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificatePage;

import java.util.List;

@UseCase
public class ListRegularCertificatesService implements ListRegularCertificatesUseCase {

    private final RegularCertificateReader reader;

    public ListRegularCertificatesService(RegularCertificateReader reader) {
        this.reader = reader;
    }

    @Override
    public List<RegularCertificateResponse> list(Long studentId, RegularCertificateStatus status) {
        return reader.list(studentId, status);
    }

    @Override
    public CertificatePage<RegularCertificateResponse> listPage(Long studentId, RegularCertificateStatus status, Integer page, Integer size) {
        return reader.listPage(studentId, status, page, size);
    }
}
