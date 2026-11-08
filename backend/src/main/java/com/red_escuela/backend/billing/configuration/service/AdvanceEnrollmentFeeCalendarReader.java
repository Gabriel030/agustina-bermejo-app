package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.AdvanceEnrollmentFeeCalendar;
import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.configuration.exception.NoEffectiveConfigurationException;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Component
public class AdvanceEnrollmentFeeCalendarReader {
    private static final int FIRST_SLOT = 1;

    private final ProgramYearBillingTemplateRepository templateRepository;
    private final BillingConfigurationReader configurationReader;
    private final SlotWindowResolver windowResolver;
    private final Clock clock;

    public AdvanceEnrollmentFeeCalendarReader(ProgramYearBillingTemplateRepository templateRepository, BillingConfigurationReader configurationReader,
                                              SlotWindowResolver windowResolver, Clock clock) {
        this.templateRepository = templateRepository;
        this.configurationReader = configurationReader;
        this.windowResolver = windowResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<AdvanceEnrollmentFeeCalendar> calendarOf(ProgramYearBillingTemplate template) {
        return calendarOf(template, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public Optional<AdvanceEnrollmentFeeCalendar> calendarOf(ProgramYearBillingTemplate template, LocalDate today) {
        try {
            YearBillingTerms terms = configurationReader.requireYearTermsEffectiveOn(template.programId(), template.termId(), today);
            YearCycle cycle = YearCycle.of(template, terms, today.getYear());
            return AdvanceEnrollmentFeeCalendar.of(windowResolver.windowOf(template.programId(), cycle, FIRST_SLOT, today), cycle);
        } catch (NoEffectiveConfigurationException noConfiguration) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> windowClosesOnOfTemplate(Long templateId, LocalDate asOf) {
        return templateRepository.findById(templateId).flatMap(template -> calendarOf(template, asOf)).map(AdvanceEnrollmentFeeCalendar::windowClosesOn);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> windowClosesOnWith(ProgramYearBillingTemplate template, YearBillingTerms proposedTerms) {
        LocalDate today = LocalDate.now(clock);
        YearCycle cycle = YearCycle.of(template, proposedTerms, today.getYear());
        return windowResolver.windowOf(template.programId(), cycle, FIRST_SLOT, today).map(window -> window.closesOn());
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> regularEmissionStartOf(Long programId, int lectiveYear) {
        return templateRepository.findAllByProgramId(programId).stream()
                .filter(template -> Objects.equals(template.termYear(), lectiveYear))
                .map(this::calendarOf)
                .flatMap(Optional::stream)
                .map(AdvanceEnrollmentFeeCalendar::regularEmissionStart)
                .min(LocalDate::compareTo);
    }
}
