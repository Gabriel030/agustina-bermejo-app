package com.red_escuela.backend.billing.annualupfront.dto;

import com.red_escuela.backend.billing.annualupfront.service.RegisteredAnnualUpfrontRequest;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;

public record AnnualUpfrontRequestResponse(Long id, String status, Long invoiceId, LocalDateTime paymentDeadline, Money total, LocalDateTime acceptedAt, String acceptedTermsVersion) {

    public static AnnualUpfrontRequestResponse from(RegisteredAnnualUpfrontRequest registered) {
        return new AnnualUpfrontRequestResponse(registered.id(), registered.status(), registered.invoiceId(), registered.paymentDeadline(), registered.total(), registered.acceptedAt(),
                registered.acceptedTermsVersion());
    }
}
