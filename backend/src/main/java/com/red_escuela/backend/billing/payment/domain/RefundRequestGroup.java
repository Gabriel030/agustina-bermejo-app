package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

public record RefundRequestGroup(Long refundRequestId, Long authorizationRequestId, LocalDate requestedOn, List<Refund> parts) {

    public RefundRequestGroup {
        parts = List.copyOf(parts);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("A refund request group needs at least one part");
        }
    }

    public Money totalAmount() {
        return sumOfPartsMatching(refund -> true);
    }

    public Money completedAmount() {
        return sumOfPartsMatching(refund -> refund.getStatus() == RefundStatus.COMPLETED);
    }

    public Money pendingAmount() {
        return sumOfPartsMatching(Refund::isPending);
    }

    private Money sumOfPartsMatching(Predicate<Refund> selection) {
        Money zero = Money.zero(parts.get(0).getAmount().value().currency());
        return parts.stream().filter(selection).map(refund -> refund.getAmount().value()).reduce(zero, Money::plus);
    }
}
