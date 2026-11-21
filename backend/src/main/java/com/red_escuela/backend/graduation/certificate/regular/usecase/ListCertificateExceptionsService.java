package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListCertificateExceptionsService implements ListCertificateExceptionsUseCase {

    private final CertificateExceptionReader specialist;

    public ListCertificateExceptionsService(CertificateExceptionReader specialist) {
        this.specialist = specialist;
    }

    @Override
    public List<CertificateExceptionResponse> list(boolean seesAll, Long studentId, CertificateExceptionStatus status) {
        return specialist.list(seesAll, studentId, status);
    }
}
