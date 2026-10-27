package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.PendingRefundOfPayment;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record PendingRefundResponse(Long refundId, String status, Money amount, LocalDate requestedOn) {

    public static PendingRefundResponse from(PendingRefundOfPayment refund) {
        return new PendingRefundResponse(refund.refundId(), refund.status().name(), refund.amount().value(), refund.requestedOn());
    }
}
