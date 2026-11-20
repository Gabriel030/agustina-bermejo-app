package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateRequestResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateRequestRejecter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectRegularCertificateRequestService implements RejectRegularCertificateRequestUseCase {

    private final RegularCertificateRequestRejecter rejecter;

    public RejectRegularCertificateRequestService(RegularCertificateRequestRejecter rejecter) {
        this.rejecter = rejecter;
    }

    @Override
    public RegularCertificateRequestResponse reject(Long requestId, String reason) {
        return rejecter.reject(requestId, reason);
    }
}
