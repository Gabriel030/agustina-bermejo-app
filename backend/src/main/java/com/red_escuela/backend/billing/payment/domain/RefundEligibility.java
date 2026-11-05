package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public record RefundEligibility(Currency currency, NonNegativeMoney unallocatedCredit, NonNegativeMoney reservedByPendingRefunds, NonNegativeMoney availableToRefund,
                                boolean hasOverdueDebt, PolicyViolation blockedBy, List<RefundablePaymentView> payments) {

    public RefundEligibility {
        payments = List.copyOf(payments);
    }

    public static RefundEligibility withoutAccount(Currency currency, PolicyViolation blockedBy) {
        NonNegativeMoney zero = NonNegativeMoney.zero(currency);
        return new RefundEligibility(currency, zero, zero, zero, false, blockedBy, List.of());
    }
}
