package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateStatus;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificatePage;

import java.util.List;

public interface ListRegularCertificatesUseCase {

    List<RegularCertificateResponse> list(Long studentId, RegularCertificateStatus status);

    CertificatePage<RegularCertificateResponse> listPage(Long studentId, RegularCertificateStatus status, Integer page, Integer size);
}
