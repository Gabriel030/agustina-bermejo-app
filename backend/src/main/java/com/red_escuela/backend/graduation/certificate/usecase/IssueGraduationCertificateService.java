package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificateIssuer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class IssueGraduationCertificateService implements IssueGraduationCertificateUseCase {

    private final GraduationCertificateIssuer issuer;

    public IssueGraduationCertificateService(GraduationCertificateIssuer issuer) {
        this.issuer = issuer;
    }

    @Override
    public GraduationCertificateResponse issue(IssueGraduationCertificateRequest request) {
        return issuer.issue(request.kind(), request.enrollmentId());
    }
}
