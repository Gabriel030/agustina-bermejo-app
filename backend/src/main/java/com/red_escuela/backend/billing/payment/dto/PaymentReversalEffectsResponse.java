package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

public record PaymentReversalEffectsResponse(boolean calculated, List<InvoiceImpactResponse> reopenedInvoices, List<InvoiceImpactResponse> settledInvoices,
                                             List<LateFeeReinstatementResponse> lateFeeReinstatements) {

    public record InvoiceImpactResponse(Long invoiceId, String paymentStatusBefore, String paymentStatusAfter, Money outstandingBefore, Money outstandingAfter,
                                        Money creditReversed) {

        static InvoiceImpactResponse from(PaymentReversalEffects.InvoiceImpact impact) {
            return new InvoiceImpactResponse(impact.invoiceId(), impact.statusBefore().name(), impact.statusAfter().name(), impact.outstandingBefore().value(),
                    impact.outstandingAfter().value(), impact.creditReversed().value());
        }
    }

    public record LateFeeReinstatementResponse(Long invoiceId, int days, Money amount, LocalDate firstDay, LocalDate lastDay, boolean settledByAccountCredit) {

        static LateFeeReinstatementResponse from(PaymentReversalEffects.LateFeeReinstatementImpact impact) {
            return new LateFeeReinstatementResponse(impact.originInvoiceId(), impact.chargedDays(), impact.amount().value(), impact.firstDay(), impact.lastDay(),
                    impact.settledByAccountCredit());
        }
    }

    public static PaymentReversalEffectsResponse from(PaymentReversalEffects effects) {
        return new PaymentReversalEffectsResponse(effects.calculated(), effects.reopenedInvoices().stream().map(InvoiceImpactResponse::from).toList(),
                effects.settledInvoices().stream().map(InvoiceImpactResponse::from).toList(),
                effects.lateFeeReinstatements().stream().map(LateFeeReinstatementResponse::from).toList());
    }
}
