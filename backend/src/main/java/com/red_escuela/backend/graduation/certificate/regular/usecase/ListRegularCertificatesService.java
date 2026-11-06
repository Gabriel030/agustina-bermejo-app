package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;
import com.red_escuela.backend.graduation.certificate.regular.service.RegularCertificateReader;
import com.red_escuela.backend.shared.UseCase;

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
}
