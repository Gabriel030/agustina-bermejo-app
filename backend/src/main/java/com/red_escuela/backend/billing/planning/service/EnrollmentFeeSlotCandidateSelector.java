package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotDueThisMonthSpecification;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotNotBeforeEntrySpecification;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeSlotPlanningContext;
import com.red_escuela.backend.billing.planning.policy.PlanningSpecification;

import java.util.List;
import java.util.stream.IntStream;

public final class EnrollmentFeeSlotCandidateSelector {
    private final PlanningSpecification<EnrollmentFeeSlotPlanningContext> candidate =
            new EnrollmentFeeSlotDueThisMonthSpecification().and(new EnrollmentFeeSlotNotBeforeEntrySpecification());

    public List<EnrollmentFeeSlotPlanningContext> selectEnrollmentFeeSlots(ProgramInvoicePlanningFacts facts, boolean catchUp) {
        return facts.cycles().all().stream()
                .flatMap(cycle -> IntStream.rangeClosed(1, cycle.slotCount())
                        .mapToObj(slot -> EnrollmentFeeSlotPlanningContext.of(facts, cycle, slot, catchUp)))
                .filter(candidate::isSatisfiedBy)
                .toList();
    }
}
