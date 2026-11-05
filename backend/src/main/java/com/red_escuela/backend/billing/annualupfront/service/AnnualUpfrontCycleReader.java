package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.billing.configuration.exception.NoEffectiveConfigurationException;
import com.red_escuela.backend.billing.configuration.service.YearCycleFactory;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.domain.YearCycles;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Optional;

@Component
public class AnnualUpfrontCycleReader {
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final YearCycleFactory cycleFactory;

    public AnnualUpfrontCycleReader(ProgramYearBillingTemplateRepository templateRepository, YearCycleFactory cycleFactory) {
        this.templateRepository = templateRepository;
        this.cycleFactory = cycleFactory;
    }

    public Optional<YearCycle> findCycleToQuote(Long programId, Long preferredTemplateId, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        YearCycles cycles;
        try {
            cycles = new YearCycles(cycleFactory.cyclesOf(templateRepository.findAllByProgramId(programId), today.getYear()), preferredTemplateId);
        } catch (NoEffectiveConfigurationException noConfiguration) {
            return Optional.empty();
        }
        return cycles.covering(month).or(() -> cycles.all().stream().filter(cycle -> cycle.start().isAfter(month)).min(Comparator.comparing(YearCycle::start)));
    }
}
