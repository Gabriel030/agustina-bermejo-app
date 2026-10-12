package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotPlanningContext;

import java.time.LocalDate;
import java.time.YearMonth;

public final class EntryEnrollmentFeeDueDateStrategy implements EnrollmentFeeDueDateStrategy {

    public boolean appliesTo(EnrollmentFeeSlotPlanningContext context) {
        return context.entry() && context.facts().cursadaStart() != null;
    }

    public YearMonth periodOf(EnrollmentFeeSlotPlanningContext context) {
        return context.facts().entryMonth();
    }

    public LocalDate dueDateOf(EnrollmentFeeSlotPlanningContext context) {
        LocalDate courseStart = context.facts().cursadaStart();
        return courseStart.isBefore(context.facts().today())
                ? context.facts().today().plusDays(context.terms().lateEnrollmentFeeDueDays())
                : courseStart;
    }
}
