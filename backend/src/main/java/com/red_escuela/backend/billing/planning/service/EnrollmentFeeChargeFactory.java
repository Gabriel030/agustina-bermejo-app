package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.planning.domain.PlannedInvoice;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotPlanningContext;

import java.util.List;

public final class EnrollmentFeeChargeFactory {
    private final List<EnrollmentFeeDueDateStrategy> strategies = List.of(new EntryEnrollmentFeeDueDateStrategy(), new ReenrollmentFeeDueDateStrategy());

    public PlannedInvoice createEnrollmentFeeCharge(EnrollmentFeeSlotPlanningContext context) {
        EnrollmentFeeDueDateStrategy strategy = strategies.stream().filter(candidate -> candidate.appliesTo(context)).findFirst().orElseThrow();
        return new PlannedInvoice(FeeType.ENROLLMENT_FEE, strategy.periodOf(context), strategy.dueDateOf(context), context.facts().today(),
                context.cycle().templateId(), context.slot(),
                "matrícula del ciclo " + context.cycle().termStart().getYear() + " (" + context.slotLabel() + ")", context.entry(), false);
    }
}
