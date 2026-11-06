package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;

public interface IssueGraduationCertificateUseCase {

    GraduationCertificateResponse issue(IssueGraduationCertificateRequest request);
}
