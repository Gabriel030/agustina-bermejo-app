package com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RequestApprovedSubjectsCertificateRequest;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RequestApprovedSubjectsCertificateService implements RequestApprovedSubjectsCertificateUseCase {

    @Override
    public ApprovedSubjectsCertificateResponse request(RequestApprovedSubjectsCertificateRequest request) {
        throw new DocumentOperationPendingException();
    }
}
