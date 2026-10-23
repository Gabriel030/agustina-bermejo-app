package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class NewestPaymentFirstRefundSplitter {

    private static final Comparator<RefundablePayment> NEWEST_FIRST = Comparator.comparing(RefundablePayment::paidOn).reversed()
            .thenComparing(Comparator.comparing(RefundablePayment::paymentId).reversed());

    private NewestPaymentFirstRefundSplitter() {
    }

    public static List<RefundPart> splitAmountAcross(Money requestedAmount, List<RefundablePayment> payments) {
        Objects.requireNonNull(requestedAmount, "requestedAmount");
        Objects.requireNonNull(payments, "payments");
        List<RefundablePayment> newestFirst = payments.stream().filter(payment -> !payment.refundable().value().isZero()).sorted(NEWEST_FIRST).toList();
        List<RefundPart> parts = new ArrayList<>();
        Money pending = requestedAmount;
        for (RefundablePayment payment : newestFirst) {
            if (pending.isZero()) {
                break;
            }
            Money taken = payment.refundable().value().isGreaterThan(pending) ? pending : payment.refundable().value();
            parts.add(new RefundPart(payment.paymentId(), payment.methodType(), NonNegativeMoney.of(taken), payment.destination()));
            pending = pending.minus(taken);
        }
        if (!pending.isZero()) {
            throw new IllegalArgumentException("The refundable amount of the payments does not cover the requested amount");
        }
        return List.copyOf(parts);
    }
}
