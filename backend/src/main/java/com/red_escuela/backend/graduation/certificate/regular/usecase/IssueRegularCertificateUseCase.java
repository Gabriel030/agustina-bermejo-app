package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.IssueRegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;

public interface IssueRegularCertificateUseCase {

    RegularCertificateResponse issue(IssueRegularCertificateRequest request);
}
