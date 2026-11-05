package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;

public record PaymentReversalEffects(boolean calculated, List<InvoiceImpact> reopenedInvoices, List<InvoiceImpact> settledInvoices,
                                     List<LateFeeReinstatementImpact> lateFeeReinstatements) {

    public PaymentReversalEffects {
        reopenedInvoices = List.copyOf(reopenedInvoices);
        settledInvoices = List.copyOf(settledInvoices);
        lateFeeReinstatements = List.copyOf(lateFeeReinstatements);
    }

    public static PaymentReversalEffects notCalculated() {
        return new PaymentReversalEffects(false, List.of(), List.of(), List.of());
    }

    public static PaymentReversalEffects calculated(List<InvoiceImpact> reopenedInvoices, List<InvoiceImpact> settledInvoices,
                                                    List<LateFeeReinstatementImpact> lateFeeReinstatements) {
        return new PaymentReversalEffects(true, reopenedInvoices, settledInvoices, lateFeeReinstatements);
    }

    public record InvoiceImpact(Long invoiceId, InvoicePaymentStatus statusBefore, InvoicePaymentStatus statusAfter, NonNegativeMoney outstandingBefore,
                                NonNegativeMoney outstandingAfter, NonNegativeMoney creditReversed) {
    }

    public record LateFeeReinstatementImpact(Long originInvoiceId, LocalDate firstDay, LocalDate lastDay, int chargedDays, NonNegativeMoney amount,
                                             boolean settledByAccountCredit) {
    }
}
