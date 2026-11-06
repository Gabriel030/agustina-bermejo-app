package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.AnnualUpfrontCashPaymentResult;
import com.red_escuela.backend.billing.payment.dto.RegisterAnnualUpfrontCashPaymentRequest;
import com.red_escuela.backend.billing.payment.service.AnnualUpfrontCashPaymentOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class RegisterAnnualUpfrontCashPaymentService implements RegisterAnnualUpfrontCashPaymentUseCase {
    private final AnnualUpfrontCashPaymentOperationRunner operationRunner;

    public RegisterAnnualUpfrontCashPaymentService(AnnualUpfrontCashPaymentOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public AnnualUpfrontCashPaymentResult registerAnnualUpfrontCashPayment(Long studentId, Long requestId, RegisterAnnualUpfrontCashPaymentRequest request, String idempotencyKey) {
        return operationRunner.run(studentId, requestId, request, idempotencyKey);
    }
}
