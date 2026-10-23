package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;

public record RefundResponse(Long id, Long refundRequestId, Long paymentId, String status, String methodType, Money amount, String destinationAccount,
                             String destinationHolder, Long authorizationRequestId, LocalDate requestedOn, LocalDate approvedOn, LocalDate completedOn,
                             String completionReference, String failureReason, String rejectionReason) {

    public static RefundResponse from(Refund refund) {
        return new RefundResponse(refund.getId(), refund.getRefundRequestId(), refund.getPaymentId(), refund.getStatus().name(), refund.getMethodType().name(),
                refund.getAmount().value(), refund.getDestinationAccount(), refund.getDestinationHolder(), refund.getAuthorizationRequestId(), refund.getRequestedOn(),
                refund.getApprovedOn(), refund.getCompletedOn(), refund.getCompletionReference(), refund.getFailureReason(), refund.getRejectionReason());
    }
}
