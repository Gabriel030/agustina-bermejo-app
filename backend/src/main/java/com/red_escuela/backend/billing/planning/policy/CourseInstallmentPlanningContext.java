package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;

import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;

public record CourseInstallmentPlanningContext(CourseInvoicePlanningFacts facts, YearMonth period, long installmentsIssued)
        implements InstallmentCapContext, InstallmentPeriodContext {

    public Optional<Integer> installmentCap() {
        return Optional.ofNullable(facts.durationMonths());
    }

    public Set<YearMonth> issuedPeriods() {
        return facts.issuedPeriods();
    }
}
