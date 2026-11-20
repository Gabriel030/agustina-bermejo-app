package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateBatchResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateBatchRequest;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class IssueGraduationCertificateBatchService implements IssueGraduationCertificateBatchUseCase {

    @Override
    public GraduationCertificateBatchResponse issueBatch(IssueGraduationCertificateBatchRequest request) {
        throw new GraduationCertificateOperationPendingException();
    }
}
