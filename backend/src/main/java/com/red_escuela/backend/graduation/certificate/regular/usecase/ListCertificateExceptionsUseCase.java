package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;

import java.util.List;

public interface ListCertificateExceptionsUseCase {

    List<CertificateExceptionResponse> list(boolean seesAll, Long studentId, CertificateExceptionStatus status);
}
