package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequest;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontRequestStatus;

public record AnnualUpfrontRequestPaymentFacts(Long requestId, Long invoiceId, Long programEnrollmentId, AnnualUpfrontRequestStatus status) {

    public static AnnualUpfrontRequestPaymentFacts of(AnnualUpfrontRequest request) {
        return new AnnualUpfrontRequestPaymentFacts(request.getId(), request.getInvoiceId(), request.getProgramEnrollmentId(), request.getStatus());
    }

    public boolean isAwaitingPayment() {
        return status == AnnualUpfrontRequestStatus.AWAITING_PAYMENT;
    }

    public boolean isDiscarded() {
        return status == AnnualUpfrontRequestStatus.DISCARDED;
    }

    public boolean isPaymentAlreadyInitiated() {
        return status == AnnualUpfrontRequestStatus.PAYMENT_UNDER_REVIEW || status == AnnualUpfrontRequestStatus.CONFIRMED;
    }
}
