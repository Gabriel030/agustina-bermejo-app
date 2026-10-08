package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;

import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

public final class CoursePlanningPeriods {

    public List<YearMonth> periodsToPlan(CourseInvoicePlanningFacts facts) {
        return facts.cadence() == PaymentPlanCadence.MONTHLY ? List.of(facts.month()) : upfrontPeriods(facts);
    }

    public CourseInstallmentMode modeOf(CourseInvoicePlanningFacts facts) {
        return facts.cadence() == PaymentPlanCadence.MONTHLY ? CourseInstallmentMode.MONTHLY : CourseInstallmentMode.UPFRONT;
    }

    private List<YearMonth> upfrontPeriods(CourseInvoicePlanningFacts facts) {
        return facts.upfrontFrom() == null || facts.upfrontScopeMonths() == null ? List.of()
                : Stream.iterate(facts.upfrontFrom(), period -> period.plusMonths(1)).limit(Math.max(0, facts.upfrontScopeMonths())).toList();
    }
}
