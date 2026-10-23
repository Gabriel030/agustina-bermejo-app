package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

public record PaymentReversalResponse(Long paymentId, Long chargebackId, String paymentStatus, Money amount, LocalDate chargedBackOn, LocalDate effectiveDate,
                                      LocalDate reversedOn, String reference, List<Long> reopenedInvoiceIds, List<Long> settledInvoiceIds) {

    public static PaymentReversalResponse from(PaymentReversalResult result) {
        return new PaymentReversalResponse(result.paymentId(), result.chargebackId(), result.paymentStatus().name(), result.amount().value(), result.chargedBackOn(),
                result.effectiveDate(), result.reversedOn(), result.reference(), result.invoicesReopened(), result.invoicesSettled());
    }
}
