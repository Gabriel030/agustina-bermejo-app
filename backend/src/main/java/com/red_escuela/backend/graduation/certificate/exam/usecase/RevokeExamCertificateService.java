package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateResponse;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateRevoker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeExamCertificateService implements RevokeExamCertificateUseCase {

    private final ExamCertificateRevoker delegate;

    public RevokeExamCertificateService(ExamCertificateRevoker delegate) {
        this.delegate = delegate;
    }

    @Override
    public ExamCertificateResponse revoke(Long id, String reason) {
        return delegate.revoke(id, reason);
    }
}
