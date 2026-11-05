package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalPreview;
import com.red_escuela.backend.shared.domain.Money;

import java.util.List;

public record PaymentReversalPreviewResponse(Long paymentId, String paymentStatus, boolean allowed, RefundBlockResponse blockedBy, Money creditToReverse,
                                             List<PendingRefundResponse> pendingRefunds, List<Long> invoiceIdsFundedByPayment, PaymentReversalEffectsResponse effects) {

    public static PaymentReversalPreviewResponse from(PaymentReversalPreview preview) {
        return new PaymentReversalPreviewResponse(preview.paymentId(), preview.paymentStatus().name(), preview.allowed(), RefundBlockResponse.from(preview.blockedBy()),
                preview.creditToReverse().value(), preview.pendingRefunds().stream().map(PendingRefundResponse::from).toList(), preview.invoiceIdsFundedByPayment(),
                PaymentReversalEffectsResponse.from(preview.effects()));
    }
}
