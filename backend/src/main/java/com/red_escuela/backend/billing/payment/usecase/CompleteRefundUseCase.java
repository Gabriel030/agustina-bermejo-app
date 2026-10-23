package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.dto.CompleteRefundRequest;

public interface CompleteRefundUseCase {
    Refund completeRefund(Long refundId, CompleteRefundRequest request);
}
