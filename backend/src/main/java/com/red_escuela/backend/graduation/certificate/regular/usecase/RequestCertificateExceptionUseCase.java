package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RequestCertificateExceptionCommand;

public interface RequestCertificateExceptionUseCase {

    CertificateExceptionResponse request(RequestCertificateExceptionCommand command);
}
