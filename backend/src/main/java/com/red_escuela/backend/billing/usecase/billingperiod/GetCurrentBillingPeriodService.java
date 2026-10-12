package com.red_escuela.backend.billing.usecase.billingperiod;

import java.time.Clock;
import com.red_escuela.backend.billing.entity.BillingPeriod;
import com.red_escuela.backend.billing.repository.BillingPeriodRepository;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDate;

@UseCase
public class GetCurrentBillingPeriodService implements GetCurrentBillingPeriodUseCase {

    private final BillingPeriodRepository billingPeriodRepository;
    private final Clock clock;

    public GetCurrentBillingPeriodService(BillingPeriodRepository billingPeriodRepository, Clock clock) {
        this.billingPeriodRepository = billingPeriodRepository;
        this.clock = clock;
    }

    @Override
    public BillingPeriod getCurrent() {
        return billingPeriodRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate.now(clock)).orElse(null);
    }
}
