package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.RefundRequestResult;
import com.red_escuela.backend.billing.payment.dto.RequestRefundRequest;

public interface RequestRefundUseCase {
    RefundRequestResult requestOwnRefund(Long studentId, RequestRefundRequest request, String idempotencyKey);

    RefundRequestResult requestRefundOnBehalfOfStudent(Long studentId, RequestRefundRequest request, String idempotencyKey);
}
