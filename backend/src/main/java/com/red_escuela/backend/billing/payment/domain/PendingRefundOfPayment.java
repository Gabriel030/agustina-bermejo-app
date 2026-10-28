package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record PendingRefundOfPayment(Long refundId, RefundStatus status, NonNegativeMoney amount, LocalDate requestedOn) {

    public static PendingRefundOfPayment of(Refund refund) {
        return new PendingRefundOfPayment(refund.getId(), refund.getStatus(), refund.getAmount(), refund.getRequestedOn());
    }
}
