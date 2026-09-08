package com.red_escuela.backend.billing.usecase.paymentplan;

import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;

public interface CreatePaymentPlanOptionForCourseUseCase {
    PaymentPlanOption create(Long courseId, CreatePaymentPlanOptionRequest request);
}
