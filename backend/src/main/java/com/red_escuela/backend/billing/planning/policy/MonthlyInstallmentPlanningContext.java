package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.planning.domain.LectiveYearBillingTerms;
import com.red_escuela.backend.billing.planning.domain.LectiveYearCycleResolution;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;

import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

public record MonthlyInstallmentPlanningContext(ProgramInvoicePlanningFacts facts, LectiveYearCycleResolution cycleResolution)
        implements ProgramFactsContext, InstallmentCapContext, InstallmentPeriodContext {

    public YearCycle installmentCycle() {
        return cycleResolution.installmentCycle().orElseThrow();
    }

    public LectiveYearBillingTerms terms() {
        return installmentCycle().terms();
    }

    public long installmentsIssued() {
        return facts.recurringPeriods().stream().filter(installmentCycle()::installmentRangeContains).count();
    }

    public Optional<Integer> installmentCap() {
        return Optional.of(terms().monthlyInstallmentsCount());
    }

    public YearMonth period() {
        return facts.month();
    }

    public Set<YearMonth> issuedPeriods() {
        return facts.recurringPeriods();
    }

    public BillingConsultation consultation() {
        return BillingConsultation.PLANNING;
    }

    public String notIssuedDetail(String cause) {
        return "no se emite cuota: " + cause;
    }
}
