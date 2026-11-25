package com.red_escuela.backend.billing.usecase.paymentplan;

import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.planning.service.SelectPaymentPlanForProgramTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SelectPaymentPlanForProgramService implements SelectPaymentPlanForProgramUseCase {
    private final SelectPaymentPlanForProgramTransaction transaction;

    public SelectPaymentPlanForProgramService(SelectPaymentPlanForProgramTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public PaymentPlanSelection select(Long programEnrollmentId, SelectPaymentPlanRequest request) {
        return transaction.select(programEnrollmentId, request);
    }
}
