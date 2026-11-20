package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionRequester;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RequestCertificateExceptionCommand;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RequestCertificateExceptionService implements RequestCertificateExceptionUseCase {

    private final CertificateExceptionRequester specialist;

    public RequestCertificateExceptionService(CertificateExceptionRequester specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateExceptionResponse request(RequestCertificateExceptionCommand command) {
        return specialist.request(command);
    }
}
