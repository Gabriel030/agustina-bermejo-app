package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.IssueRegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.specialist.IssueRegularCertificateCommand;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateIssuer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class IssueRegularCertificateService implements IssueRegularCertificateUseCase {

    private final RegularCertificateIssuer issuer;

    public IssueRegularCertificateService(RegularCertificateIssuer issuer) {
        this.issuer = issuer;
    }

    @Override
    public RegularCertificateResponse issue(IssueRegularCertificateRequest request) {
        return issuer.issue(new IssueRegularCertificateCommand(request.kind(), request.enrollmentId(),
                request.standingConfirmed(), request.standingEvidence(), RegularCertificateChannel.STAFF));
    }
}
