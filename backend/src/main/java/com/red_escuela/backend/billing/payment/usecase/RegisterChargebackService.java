package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.billing.payment.dto.RegisterChargebackRequest;
import com.red_escuela.backend.billing.payment.service.PaymentReversalOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class RegisterChargebackService implements RegisterChargebackUseCase {
    private final PaymentReversalOperationRunner operationRunner;

    public RegisterChargebackService(PaymentReversalOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public PaymentReversalResult registerChargeback(Long paymentId, RegisterChargebackRequest request) {
        return operationRunner.runChargebackRegistration(paymentId, request.amount(), request.chargedBackOn(), request.reference());
    }
}
