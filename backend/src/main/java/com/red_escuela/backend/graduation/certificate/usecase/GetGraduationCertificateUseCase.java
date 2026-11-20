package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateDetailResponse;

public interface GetGraduationCertificateUseCase {

    GraduationCertificateDetailResponse get(Long id);
}
