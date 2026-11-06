package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record AnnualUpfrontCashPaymentResult(Long paymentId, Long studentCurrentAccountId, Long requestId, Long invoiceId, NonNegativeMoney amount, LocalDate paidOn) {
}
