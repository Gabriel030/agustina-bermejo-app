package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateBillingPeriodRequest;
import com.red_escuela.backend.entity.BillingPeriod;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.repository.BillingPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
