package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.shared.domain.Money;

public record ApprovedPaymentCreditingResult(AccountCreditAllocationResult allocation, Money leftAsAccountCredit) {

    public boolean leavesCreditInFavor() {
        return leftAsAccountCredit.isGreaterThan(Money.zero(leftAsAccountCredit.currency()));
    }
}
