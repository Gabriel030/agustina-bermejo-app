package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionRequester;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RequestCertificateExceptionCommand;
import com.red_escuela.backend.graduation.certificate.regular.dto.RequestCertificateExceptionRequest;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RequestCertificateExceptionService implements RequestCertificateExceptionUseCase {

    private final CertificateExceptionRequester specialist;

    public RequestCertificateExceptionService(CertificateExceptionRequester specialist) {
        this.specialist = specialist;
    }

    @Override
    public CertificateExceptionResponse request(RequestCertificateExceptionRequest request) {
        return specialist.request(new RequestCertificateExceptionCommand(request.kind(), request.enrollmentId(), request.organismKind(),
                request.organismReference(), request.reason()));
    }
}
