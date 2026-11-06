package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;

public interface GetRegularCertificateUseCase {

    RegularCertificateResponse get(Long id);
}
