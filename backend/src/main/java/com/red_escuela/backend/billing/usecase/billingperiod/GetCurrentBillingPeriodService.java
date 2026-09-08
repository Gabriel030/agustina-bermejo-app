package com.red_escuela.backend.billing.usecase.billingperiod;

import com.red_escuela.backend.billing.entity.BillingPeriod;
import com.red_escuela.backend.billing.repository.BillingPeriodRepository;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDate;

@UseCase
public class GetCurrentBillingPeriodService implements GetCurrentBillingPeriodUseCase {

    private final BillingPeriodRepository billingPeriodRepository;

    public GetCurrentBillingPeriodService(BillingPeriodRepository billingPeriodRepository) {
        this.billingPeriodRepository = billingPeriodRepository;
    }

    @Override
    public BillingPeriod getCurrent() {
        return billingPeriodRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate.now()).orElse(null);
    }
}
