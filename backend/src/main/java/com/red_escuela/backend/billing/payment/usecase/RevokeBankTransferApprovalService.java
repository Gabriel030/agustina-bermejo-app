package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.billing.payment.service.PaymentReversalOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class RevokeBankTransferApprovalService implements RevokeBankTransferApprovalUseCase {
    private final PaymentReversalOperationRunner operationRunner;

    public RevokeBankTransferApprovalService(PaymentReversalOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasRole('DIRECTOR')")
    public PaymentReversalResult revokeBankTransferApproval(Long paymentId, String reason) {
        return operationRunner.runRevocation(paymentId, reason);
    }
}
