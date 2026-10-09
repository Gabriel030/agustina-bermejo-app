package com.red_escuela.backend.billing.configuration.service;

import com.red_escuela.backend.billing.configuration.domain.YearBillingTerms;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Component
public class YearCycleFactory {
    private final BillingConfigurationReader configurationReader;
    private final Clock clock;

    public YearCycleFactory(BillingConfigurationReader configurationReader, Clock clock) {
        this.configurationReader = configurationReader;
        this.clock = clock;
    }

    public YearCycle cycleOf(ProgramYearBillingTemplate template, int fallbackYear) {
        return cycleOf(template, fallbackYear, LocalDate.now(clock));
    }

    public YearCycle cycleOf(ProgramYearBillingTemplate template, int fallbackYear, LocalDate factDate) {
        YearBillingTerms terms = configurationReader.requireYearTermsEffectiveOn(template.programId(), template.getTerm().getId(), factDate);
        return YearCycle.of(template, terms, fallbackYear);
    }

    public List<YearCycle> cyclesOf(Collection<ProgramYearBillingTemplate> templates, int fallbackYear) {
        return templates.stream().map(template -> cycleOf(template, fallbackYear)).toList();
    }
}
