package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public List<Refund> pendingRefundsOf(Payment payment) {
        return refunds.findByPaymentIdAndStatusIn(payment.getId(), RefundStatus.PENDING).stream().sorted(Comparator.comparing(Refund::getId)).toList();
    }

    public Map<Long, List<Refund>> pendingRefundsByPayment(Collection<Long> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        return refunds.findByPaymentIdInAndStatusIn(paymentIds, RefundStatus.PENDING).stream().sorted(Comparator.comparing(Refund::getId))
                .collect(Collectors.groupingBy(Refund::getPaymentId));
    }
}
