package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.dto.RegisterCashPaymentRequest;
import com.red_escuela.backend.billing.payment.domain.CashPaymentRegistrationResult;
import com.red_escuela.backend.billing.payment.service.CashPaymentOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class RegisterCashPaymentService implements RegisterCashPaymentUseCase {
    private final CashPaymentOperationRunner operationRunner;

    public RegisterCashPaymentService(CashPaymentOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public CashPaymentRegistrationResult registerCashPayment(Long studentId, RegisterCashPaymentRequest request, String idempotencyKey) {
        return operationRunner.runRegistration(studentId, request, idempotencyKey);
    }
}
