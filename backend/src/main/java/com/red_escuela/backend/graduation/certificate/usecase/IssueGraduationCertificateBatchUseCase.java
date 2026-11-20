package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateBatchRequest;

public interface IssueGraduationCertificateBatchUseCase {

    GraduationCertificateBatchResponse issueBatch(IssueGraduationCertificateBatchRequest request);
}
