package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

public record RefundAvailabilityCheck(Money requestedAmount, boolean hasOverdueDebt, NonNegativeMoney unallocatedCredit, NonNegativeMoney pendingRefundAmount,
                                      NonNegativeMoney refundableInManualPayments) {

    public NonNegativeMoney availableCredit() {
        return unallocatedCredit.minusNotBelowZero(pendingRefundAmount);
    }
}
