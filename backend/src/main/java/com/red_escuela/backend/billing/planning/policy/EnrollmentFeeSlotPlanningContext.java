package com.red_escuela.backend.billing.planning.policy;

import com.red_escuela.backend.billing.planning.domain.IssuedEnrollmentFee;
import com.red_escuela.backend.billing.planning.domain.LectiveYearBillingTerms;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;

import java.util.Optional;

public record EnrollmentFeeSlotPlanningContext(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot, LectiveYearBillingTerms terms,
                                               String slotLabel, boolean catchUp) implements ProgramFactsContext {

    public static EnrollmentFeeSlotPlanningContext of(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot, boolean catchUp) {
        return new EnrollmentFeeSlotPlanningContext(facts, cycle, slot, cycle.terms(), "plantilla " + cycle.templateId() + " matrícula " + slot, catchUp);
    }

    public boolean entry() {
        return facts.isEntrySlot(cycle, slot);
    }

    public Optional<IssuedEnrollmentFee> issuedFee() {
        return facts.feeOf(cycle, slot);
    }

    public BillingConsultation consultation() {
        return BillingConsultation.PLANNING;
    }

    public String notIssuedDetail(String cause) {
        return slotLabel + " no se emite: " + cause;
    }
}
