package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.RequestCertificateExceptionRequest;

public interface RequestCertificateExceptionUseCase {

    CertificateExceptionResponse request(RequestCertificateExceptionRequest request);
}
