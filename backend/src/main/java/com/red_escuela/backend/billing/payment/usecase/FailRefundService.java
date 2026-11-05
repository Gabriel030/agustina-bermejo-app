package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.dto.FailRefundRequest;
import com.red_escuela.backend.billing.payment.service.RefundOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class FailRefundService implements FailRefundUseCase {
    private final RefundOperationRunner operationRunner;

    public FailRefundService(RefundOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public Refund failRefund(Long refundId, FailRefundRequest request) {
        return operationRunner.runRefundFailure(refundId, request.reason());
    }
}
