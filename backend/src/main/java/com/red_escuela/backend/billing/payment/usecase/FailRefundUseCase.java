package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.dto.FailRefundRequest;

public interface FailRefundUseCase {
    Refund failRefund(Long refundId, FailRefundRequest request);
}
