package com.red_escuela.backend.billing.usecase.paymentplan;

import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.service.CoursePaymentPlanOptionCreation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreatePaymentPlanOptionForCourseService implements CreatePaymentPlanOptionForCourseUseCase {

    private final CoursePaymentPlanOptionCreation optionCreation;

    public CreatePaymentPlanOptionForCourseService(CoursePaymentPlanOptionCreation optionCreation) {
        this.optionCreation = optionCreation;
    }

    @Override
    public PaymentPlanOption create(Long courseId, CreatePaymentPlanOptionRequest request) {
        return optionCreation.create(courseId, request);
    }
}
