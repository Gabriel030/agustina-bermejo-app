package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PaymentPendingRefunds {
    private static final Set<RefundStatus> PENDING_STATUSES = Set.of(RefundStatus.REQUESTED, RefundStatus.APPROVED);

    private final RefundRepository refunds;

    public PaymentPendingRefunds(RefundRepository refunds) {
        this.refunds = refunds;
    }

    public Money pendingAmountOf(Payment payment) {
        return refunds.findByPaymentIdAndStatusIn(payment.getId(), PENDING_STATUSES).stream().map(Refund::getAmount).map(amount -> amount.value())
                .reduce(Money.zero(payment.getCurrency()), Money::plus);
    }
}
