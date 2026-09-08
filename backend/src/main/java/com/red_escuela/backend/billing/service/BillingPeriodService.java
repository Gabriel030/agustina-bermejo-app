package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreateBillingPeriodRequest;
import com.red_escuela.backend.billing.entity.BillingPeriod;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.repository.BillingPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import com.red_escuela.backend.identity.service.CurrentPersonService;

@Service
public class BillingPeriodService {

    private final BillingPeriodRepository billingPeriodRepository;
    private final CurrentPersonService currentPersonService;

    public BillingPeriodService(BillingPeriodRepository billingPeriodRepository, CurrentPersonService currentPersonService) {
        this.billingPeriodRepository = billingPeriodRepository;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public BillingPeriod crear(CreateBillingPeriodRequest request) {
        Person actor = currentPersonService.resolveCurrentPerson();
        BillingPeriod billingPeriod = new BillingPeriod();
        billingPeriod.setStartDay(request.startDay());
        billingPeriod.setEndDay(request.endDay());
        billingPeriod.setEffectiveFrom(request.effectiveFrom());
        billingPeriod.setCreatedBy(actor);
        return billingPeriodRepository.save(billingPeriod);
    }

    public BillingPeriod current() {
        return billingPeriodRepository.findTopByEffectiveFromLessThanEqualOrderByEffectiveFromDescCreatedAtDescIdDesc(LocalDate.now()).orElse(null);
    }
}
