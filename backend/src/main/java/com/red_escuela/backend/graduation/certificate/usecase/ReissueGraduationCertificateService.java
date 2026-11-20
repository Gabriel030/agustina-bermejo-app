package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReissueGraduationCertificateService implements ReissueGraduationCertificateUseCase {

    @Override
    public GraduationCertificateDetailResponse reissue(Long id, String reason) {
        throw new GraduationCertificateOperationPendingException();
    }
}
