package com.red_escuela.backend.billing.usecase.paymentplan;

import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.pricing.service.ProgramPaymentPlanOptionCreation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreatePaymentPlanOptionForProgramService implements CreatePaymentPlanOptionForProgramUseCase {

    private final ProgramPaymentPlanOptionCreation optionCreation;

    public CreatePaymentPlanOptionForProgramService(ProgramPaymentPlanOptionCreation optionCreation) {
        this.optionCreation = optionCreation;
    }

    @Override
    public PaymentPlanOption create(Long programId, CreatePaymentPlanOptionRequest request) {
        return optionCreation.create(programId, request);
    }
}
