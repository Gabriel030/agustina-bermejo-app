package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.dto.CompleteRefundRequest;
import com.red_escuela.backend.billing.payment.service.RefundOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class CompleteRefundService implements CompleteRefundUseCase {
    private final RefundOperationRunner operationRunner;

    public CompleteRefundService(RefundOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public Refund completeRefund(Long refundId, CompleteRefundRequest request) {
        return operationRunner.runRefundCompletion(refundId, request.completedOn(), request.reference(), request.methodType());
    }
}
