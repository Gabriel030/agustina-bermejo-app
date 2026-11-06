package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;

import java.util.List;

public interface ListRegularCertificatesUseCase {

    List<RegularCertificateResponse> list(Long studentId, RegularCertificateStatus status);
}
