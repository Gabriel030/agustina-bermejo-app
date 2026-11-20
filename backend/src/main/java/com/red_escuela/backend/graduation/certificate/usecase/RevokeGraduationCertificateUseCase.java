package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;

public interface RevokeGraduationCertificateUseCase {

    GraduationCertificateDetailResponse revoke(Long id, String reason);
}
