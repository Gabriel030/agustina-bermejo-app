package com.red_escuela.backend.billing.usecase.paymentplan;

import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;

public interface SelectPaymentPlanForProgramUseCase {
    PaymentPlanSelection select(Long programEnrollmentId, SelectPaymentPlanRequest request);
}
