package com.red_escuela.backend.billing.annualupfront.dto;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.service.RegisteredAnnualUpfrontRequest;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;

public record AnnualUpfrontRequestResponse(Long id, String status, Long invoiceId, LocalDateTime paymentDeadline, Money total, LocalDateTime acceptedAt, String acceptedTermsVersion) {

    public static AnnualUpfrontRequestResponse from(RegisteredAnnualUpfrontRequest registered) {
        AnnualUpfrontRequest request = registered.request();
        return new AnnualUpfrontRequestResponse(request.getId(), request.getStatus().name(), request.getInvoiceId(), registered.invoice().getPaymentDeadline(),
                registered.invoice().getAmounts().total().value(), request.getAcceptedAt(), request.getAcceptedTermsVersion());
    }
}
