package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;

public interface RevokeRegularCertificateUseCase {

    RegularCertificateResponse revoke(Long certificateId, String reason);
}
