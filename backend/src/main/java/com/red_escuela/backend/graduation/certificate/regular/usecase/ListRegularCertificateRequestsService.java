package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateReader;
import com.red_escuela.backend.shared.UseCase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificatePage;

import java.util.List;

@UseCase
public class ListRegularCertificateRequestsService implements ListRegularCertificateRequestsUseCase {

    private final RegularCertificateReader reader;

    public ListRegularCertificateRequestsService(RegularCertificateReader reader) {
        this.reader = reader;
    }

    @Override
    public List<RegularCertificateRequestResponse> list(Long studentId, RegularRequestOutcome outcome) {
        return reader.listRequests(studentId, outcome);
    }

    @Override
    public CertificatePage<RegularCertificateRequestResponse> listPage(Long studentId, RegularRequestOutcome outcome, Integer page, Integer size) {
        return reader.listRequestsPage(studentId, outcome, page, size);
    }
}
