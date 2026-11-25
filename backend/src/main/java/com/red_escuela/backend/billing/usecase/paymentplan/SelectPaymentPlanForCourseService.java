package com.red_escuela.backend.billing.usecase.paymentplan;

import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.run.service.SelectPaymentPlanForCourseTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SelectPaymentPlanForCourseService implements SelectPaymentPlanForCourseUseCase {
    private final SelectPaymentPlanForCourseTransaction transaction;

    public SelectPaymentPlanForCourseService(SelectPaymentPlanForCourseTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public PaymentPlanSelection select(Long courseEnrollmentId, SelectPaymentPlanRequest request) {
        return transaction.select(courseEnrollmentId, request);
    }
}
