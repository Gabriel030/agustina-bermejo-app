package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.SlotWindow;
import com.red_escuela.backend.billing.planning.domain.YearCycle;

import java.time.YearMonth;

public final class EnrollmentFeeSlotDueThisMonthSpecification implements PlanningSpecification<EnrollmentFeeSlotPlanningContext> {

    public static YearMonth emissionMonth(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot) {
        SlotWindow window = facts.windows().get(SlotWindow.key(cycle.templateId(), slot));
        return window == null ? cycle.slotPeriod(slot)
                : YearMonth.from(window.opensOn()).minusMonths(cycle.terms().enrollmentFeeLeadMonths());
    }

    public static boolean isDueNow(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot, boolean catchUp) {
        SlotWindow window = facts.windows().get(SlotWindow.key(cycle.templateId(), slot));
        return catchUp ? isDueCatchingUp(facts, cycle, slot) : isDueOnSchedule(facts, cycle, slot, window);
    }

    private static boolean isDueCatchingUp(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot) {
        return !emissionMonth(facts, cycle, slot).isAfter(facts.month()) && !cycle.slotTo(slot).isBefore(facts.month());
    }

    private static boolean isDueOnSchedule(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot, SlotWindow window) {
        return window == null ? cycle.slotPeriod(slot).equals(facts.month())
                : !emissionMonth(facts, cycle, slot).isAfter(facts.month()) && !facts.today().isAfter(window.closesOn());
    }

    public boolean isSatisfiedBy(EnrollmentFeeSlotPlanningContext candidate) {
        return isDueNow(candidate.facts(), candidate.cycle(), candidate.slot(), candidate.catchUp()) || (candidate.catchUp() && candidate.entry());
    }
}
