package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetGraduationCertificateService implements GetGraduationCertificateUseCase {

    @Override
    public GraduationCertificateDetailResponse get(Long id) {
        throw new GraduationCertificateOperationPendingException();
    }
}
