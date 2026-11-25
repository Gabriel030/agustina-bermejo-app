package com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeApprovedSubjectsCertificateService implements RevokeApprovedSubjectsCertificateUseCase {

    @Override
    public ApprovedSubjectsCertificateResponse revoke(Long id, String reason) {
        throw new DocumentOperationPendingException();
    }
}
