package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;

public final class EnrollmentFeeSlotNotBeforeEntrySpecification implements PlanningSpecification<EnrollmentFeeSlotPlanningContext> {

    public static boolean isSlotBeforeEntry(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot) {
        return facts.entryMonth() != null && cycle.slotTo(slot).isBefore(facts.entryMonth());
    }

    public boolean isSatisfiedBy(EnrollmentFeeSlotPlanningContext candidate) {
        return !isSlotBeforeEntry(candidate.facts(), candidate.cycle(), candidate.slot());
    }
}
