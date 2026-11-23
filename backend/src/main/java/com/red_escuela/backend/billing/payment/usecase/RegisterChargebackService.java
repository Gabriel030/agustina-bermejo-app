package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.billing.payment.dto.RegisterChargebackRequest;
import com.red_escuela.backend.billing.payment.service.PaymentReversalOperationRunner;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RegisterChargebackService implements RegisterChargebackUseCase {
    private final PaymentReversalOperationRunner operationRunner;

    public RegisterChargebackService(PaymentReversalOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    public PaymentReversalResult registerChargeback(Long paymentId, RegisterChargebackRequest request) {
        return operationRunner.runChargebackRegistration(paymentId, request.amount(), request.chargedBackOn(), request.reference());
    }
}
