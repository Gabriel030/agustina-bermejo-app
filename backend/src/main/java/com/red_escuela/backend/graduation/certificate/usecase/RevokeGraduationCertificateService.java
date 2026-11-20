package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevokeGraduationCertificateService implements RevokeGraduationCertificateUseCase {

    @Override
    public GraduationCertificateDetailResponse revoke(Long id, String reason) {
        throw new GraduationCertificateOperationPendingException();
    }
}
