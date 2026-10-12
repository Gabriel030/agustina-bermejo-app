package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.LectiveYearCycleResolution;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;

import java.time.YearMonth;
import java.util.Optional;

public final class LectiveYearCycleResolver {

    public LectiveYearCycleResolution resolveLectiveYearCycle(ProgramInvoicePlanningFacts facts) {
        YearMonth month = facts.month();
        Optional<YearCycle> installmentCycle = facts.cycles().forInstallment(month);
        Optional<YearCycle> covering = facts.cycles().covering(month);
        return !facts.cycles().isConfiguredFor(month) ? new LectiveYearCycleResolution.NotConfigured(month)
                : installmentCycle.or(() -> covering)
                        .<LectiveYearCycleResolution>map(cycle -> new LectiveYearCycleResolution.Configured(cycle, installmentCycle, covering.isPresent()))
                        .orElseGet(LectiveYearCycleResolution.BetweenYears::new);
    }
}
