package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.shared.domain.Money;

import java.util.List;

public record PaymentReversalEffectsResponse(boolean calculated, List<InvoiceImpactResponse> reopenedInvoices, List<InvoiceImpactResponse> settledInvoices,
                                             List<LateFeeReinstatementResponse> lateFeeReinstatements) {

    public record InvoiceImpactResponse(Long invoiceId, String paymentStatusBefore, String paymentStatusAfter, Money outstandingBefore, Money outstandingAfter) {
    }

    public record LateFeeReinstatementResponse(Long invoiceId, int days, Money amount) {
    }

    public static PaymentReversalEffectsResponse notCalculated() {
        return new PaymentReversalEffectsResponse(false, List.of(), List.of(), List.of());
    }
}
