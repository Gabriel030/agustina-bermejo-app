package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.AdvanceEnrollmentFeeCalendar;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.exception.NoEffectiveConfigurationException;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Component
public class AdvanceEnrollmentFeeCalendarReader {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final BillingConfigurationReader configurationReader;
    private final Clock clock;

    public AdvanceEnrollmentFeeCalendarReader(ProgramYearBillingTemplateRepository templateRepository, BillingConfigurationReader configurationReader, Clock clock) {
        this.templateRepository = templateRepository;
        this.configurationReader = configurationReader;
        this.clock = clock;
    }

    public Optional<AdvanceEnrollmentFeeCalendar> calendarOf(ProgramYearBillingTemplate template) {
        try {
            YearBillingTerms terms = configurationReader.requireYearTermsEffectiveOn(template.programId(), template.termId(), LocalDate.now(clock));
            return AdvanceEnrollmentFeeCalendar.of(template.termEnrollmentStartDate(), template.termEnrollmentEndDate(), template.termStartDate(), terms);
        } catch (NoEffectiveConfigurationException noConfiguration) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> regularEmissionStartOf(Long programId, int lectiveYear) {
        return templateRepository.findAllByProgramId(programId).stream()
                .filter(template -> Objects.equals(template.termYear(), lectiveYear))
                .findFirst()
                .flatMap(this::calendarOf)
                .map(AdvanceEnrollmentFeeCalendar::regularEmissionStart);
    }
}
