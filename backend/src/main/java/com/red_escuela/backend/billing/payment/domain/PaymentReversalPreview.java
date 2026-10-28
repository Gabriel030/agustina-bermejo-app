package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;
import java.util.Objects;

public record PaymentReversalPreview(Long paymentId, PaymentStatus paymentStatus, PolicyViolation blockedBy, NonNegativeMoney creditToReverse,
                                     List<PendingRefundOfPayment> pendingRefunds, List<Long> invoiceIdsFundedByPayment,
                                     PaymentReversalEffects effects) {

    public PaymentReversalPreview {
        pendingRefunds = List.copyOf(pendingRefunds);
        invoiceIdsFundedByPayment = List.copyOf(invoiceIdsFundedByPayment);
        Objects.requireNonNull(effects, "effects");
    }

    public boolean allowed() {
        return blockedBy == null;
    }
}
