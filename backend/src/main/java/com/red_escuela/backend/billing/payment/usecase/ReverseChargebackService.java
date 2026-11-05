package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.billing.payment.service.PaymentReversalOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class ReverseChargebackService implements ReverseChargebackUseCase {
    private final PaymentReversalOperationRunner operationRunner;

    public ReverseChargebackService(PaymentReversalOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public PaymentReversalResult reverseChargeback(Long chargebackId) {
        return operationRunner.runChargebackReversal(chargebackId);
    }
}
