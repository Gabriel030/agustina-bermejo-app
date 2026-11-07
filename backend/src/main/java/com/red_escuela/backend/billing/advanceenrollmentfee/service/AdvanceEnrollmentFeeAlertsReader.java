package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.billing.configuration.domain.AdvanceEnrollmentFeeAlerts;
import com.red_escuela.backend.billing.configuration.domain.AdvanceEnrollmentFeeCalendar;
import com.red_escuela.backend.billing.configuration.service.AdvanceEnrollmentFeeCalendarReader;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.exception.ProgramYearBillingTemplateNotFoundException;
import com.red_escuela.backend.billing.pricing.service.AdvanceEnrollmentFeePriceFinder;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class AdvanceEnrollmentFeeAlertsReader {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final AdvanceEnrollmentFeeCalendarReader calendarReader;
    private final AdvanceEnrollmentFeePriceFinder priceFinder;
    private final Clock clock;

    public AdvanceEnrollmentFeeAlertsReader(ProgramYearBillingTemplateRepository templateRepository, AdvanceEnrollmentFeeCalendarReader calendarReader,
                                            AdvanceEnrollmentFeePriceFinder priceFinder, Clock clock) {
        this.templateRepository = templateRepository;
        this.calendarReader = calendarReader;
        this.priceFinder = priceFinder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdvanceEnrollmentFeeAlerts alertsOf(Long templateId) {
        ProgramYearBillingTemplate template = templateRepository.findById(templateId).orElseThrow(() -> new ProgramYearBillingTemplateNotFoundException(templateId));
        boolean openingAfterEmission = calendarReader.calendarOf(template).map(AdvanceEnrollmentFeeCalendar::openingAfterRegularEmission).orElse(false);
        boolean futureYear = template.termYear() != null && template.termYear() > LocalDate.now(clock).getYear();
        boolean priceMissing = futureYear && priceFinder.findLiveVersion(template.programId(), template.termYear()).isEmpty();
        return new AdvanceEnrollmentFeeAlerts(openingAfterEmission, priceMissing);
    }
}
