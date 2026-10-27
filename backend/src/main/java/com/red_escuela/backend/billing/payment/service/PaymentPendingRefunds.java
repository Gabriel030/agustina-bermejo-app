package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;

@Component
public class PaymentPendingRefunds {
    private final RefundRepository refunds;

    public PaymentPendingRefunds(RefundRepository refunds) {
        this.refunds = refunds;
    }

    public Money pendingAmountOf(Payment payment) {
        return refunds.findByPaymentIdAndStatusIn(payment.getId(), RefundStatus.PENDING).stream().map(Refund::getAmount).map(amount -> amount.value())
                .reduce(Money.zero(payment.getCurrency()), Money::plus);
    }
}
