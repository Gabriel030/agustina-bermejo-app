package com.red_escuela.backend.billing.usecase.billingperiod;

import com.red_escuela.backend.billing.dto.CreateBillingPeriodRequest;
import com.red_escuela.backend.billing.entity.BillingPeriod;
import com.red_escuela.backend.billing.repository.BillingPeriodRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CreateBillingPeriodService implements CreateBillingPeriodUseCase {

    private final BillingPeriodRepository billingPeriodRepository;
    private final CurrentPersonService currentPersonService;

    public CreateBillingPeriodService(BillingPeriodRepository billingPeriodRepository, CurrentPersonService currentPersonService) {
        this.billingPeriodRepository = billingPeriodRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public BillingPeriod create(CreateBillingPeriodRequest request) {
        Person actor = currentPersonService.resolveCurrentPerson();
        BillingPeriod billingPeriod = new BillingPeriod();
        billingPeriod.setStartDay(request.startDay());
        billingPeriod.setEndDay(request.endDay());
        billingPeriod.setEffectiveFrom(request.effectiveFrom());
        billingPeriod.setCreatedBy(actor);
        return billingPeriodRepository.save(billingPeriod);
    }
}
