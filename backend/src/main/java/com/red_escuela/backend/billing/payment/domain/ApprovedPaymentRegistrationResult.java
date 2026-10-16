package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record ApprovedPaymentRegistrationResult(Long paymentId, Long studentCurrentAccountId, LocalDate paidOn, NonNegativeMoney amount, Long reportedInvoiceId,
                                                Money leftAsAccountCredit, Long actorPersonId) {

    public boolean leavesCreditInFavor() {
        return leftAsAccountCredit.isGreaterThan(Money.zero(leftAsAccountCredit.currency()));
    }
}
