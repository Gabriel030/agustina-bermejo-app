package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateResponse;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeExamCertificateService implements RevokeExamCertificateUseCase {

    @Override
    public ExamCertificateResponse revoke(Long id, String reason) {
        throw new DocumentOperationPendingException();
    }
}
